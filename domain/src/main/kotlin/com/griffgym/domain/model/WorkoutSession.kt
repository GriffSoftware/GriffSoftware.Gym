package com.griffgym.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class WorkoutStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    val isFinished: Boolean get() = this != IN_PROGRESS
}

/** The values a lifter actually enters for one set. */
data class SetResult(
    val weight: Weight?,
    val reps: Int?,
    val rpe: Rpe?,
    val completed: Boolean,
    val notes: String?,
)

/**
 * One logged set.
 *
 * The `planned*` fields are a snapshot taken when the session was started; editing the
 * program later can never rewrite them. The `actual*` fields are what happened.
 */
data class SetLog(
    val id: Long,
    val position: Int,
    val plannedWeight: Weight?,
    val plannedReps: Int?,
    val plannedRpe: RpeTarget?,
    val actualWeight: Weight?,
    val actualReps: Int?,
    val actualRpe: Rpe?,
    val completed: Boolean,
    val notes: String?,
) {
    val volume: TrainingVolume
        get() {
            if (!completed) return TrainingVolume.ZERO
            val weight = actualWeight ?: return TrainingVolume.ZERO
            val reps = actualReps ?: return TrainingVolume.ZERO
            return TrainingVolume.from(weight, reps)
        }

    val estimatedOneRepMax: EstimatedOneRepMax?
        get() {
            if (!completed) return null
            val weight = actualWeight ?: return null
            val reps = actualReps ?: return null
            return OneRepMaxCalculator.estimate(weight, reps)
        }

    fun withResult(result: SetResult): SetLog = copy(
        actualWeight = result.weight,
        actualReps = result.reps,
        actualRpe = result.rpe,
        completed = result.completed,
        notes = result.notes,
    )
}

data class ExerciseLog(
    val id: Long,
    val position: Int,
    val exercise: Exercise,
    val type: ExerciseType,
    val sets: List<SetLog>,
) {
    val volume: TrainingVolume get() = sets.map { it.volume }.sum()

    val plannedScheme: SetScheme?
        get() = SetScheme.from(
            sets.map {
                PlannedSet(
                    id = it.id,
                    position = it.position,
                    weight = it.plannedWeight,
                    reps = it.plannedReps,
                    targetRpe = it.plannedRpe,
                )
            },
        )

    /** Best Epley estimate produced by this exercise, ignoring unfinished sets. */
    val bestEstimatedOneRepMax: EstimatedOneRepMax?
        get() = sets.mapNotNull { it.estimatedOneRepMax }.maxByOrNull { it.weight.kilograms }
}

/**
 * A workout in flight or in the history book.
 *
 * A session is a *snapshot* of a [WorkoutTemplate], not a pointer to it — that is what
 * keeps history immune to later edits of the program.
 */
data class WorkoutSession(
    val id: Long,
    val templateId: Long?,
    val weekNumber: Int,
    val dayNumber: Int,
    val title: String,
    val isDeload: Boolean,
    val status: WorkoutStatus,
    val date: LocalDate,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val notes: String?,
    val exercises: List<ExerciseLog>,
) {
    val isActive: Boolean get() = status == WorkoutStatus.IN_PROGRESS

    val isReadOnly: Boolean get() = status.isFinished

    val duration: Duration? get() = finishedAt?.let { Duration.between(startedAt, it) }

    val totalVolume: TrainingVolume get() = exercises.map { it.volume }.sum()

    val completedSets: Int get() = exercises.sumOf { exercise -> exercise.sets.count { it.completed } }

    val totalSets: Int get() = exercises.sumOf { it.sets.size }

    val totalReps: Int
        get() = exercises.sumOf { exercise ->
            exercise.sets.filter { it.completed }.sumOf { it.actualReps ?: 0 }
        }
}

/** What the Home screen and the log tab should show right now. */
sealed interface CurrentWorkout {

    /** A session is running and must be resumed rather than started again. */
    data class Active(val session: WorkoutSession) : CurrentWorkout

    /** Nothing is running; this is the next unit of the program. */
    data class Planned(val template: WorkoutTemplate) : CurrentWorkout

    /**
     * Every unit of [cycle] has been completed and the lifter has not decided on the next
     * cycle yet.
     *
     * This is a state the app waits in, not a state it passes through: the next cycle is
     * never created without the lifter explicitly asking for it.
     */
    data class CycleCompleted(val cycle: TrainingCycle) : CurrentWorkout

    /** Nothing to train and no cycle to review — an installation with no plan at all. */
    data object NoProgram : CurrentWorkout
}
