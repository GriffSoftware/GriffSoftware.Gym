package com.griffgym.infrastructure.sync.model

import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.WorkoutStatus
import java.time.Instant
import java.time.LocalDate

/**
 * One lifter's entire training state, addressed by sync id rather than by row id.
 *
 * This is the seam between the two halves of synchronisation, and it exists because the two
 * halves have genuinely different problems. The network side deals in JSON, nullable fields
 * and a server that speaks GUIDs. The database side deals in `Long` primary keys, foreign
 * keys and an insert order that has to respect them. Neither should have to understand the
 * other, and putting the API's DTOs directly into Room would weld them together permanently.
 *
 * Everything here is identified the way the server identifies it — by sync id — because that
 * is the only identity that survives the local database being rebuilt from scratch. Turning
 * those back into row ids is the writer's job, and it is the whole reason a restore has to be
 * one transaction.
 */
internal data class CloudSnapshot(
    val exercises: List<SnapshotExercise>,
    val referenceMaxes: List<SnapshotReferenceMax>,
    /** Oldest cycle first: cycle 1 is cycle 1, and restoring must not renumber anything. */
    val cycles: List<SnapshotCycle>,
    val workouts: List<SnapshotWorkout>,
) {
    val isEmpty: Boolean
        get() = exercises.isEmpty() &&
            referenceMaxes.isEmpty() &&
            cycles.isEmpty() &&
            workouts.isEmpty()

    /** There is at most one. A second would mean two answers to "which workout am I in?". */
    val activeWorkout: SnapshotWorkout?
        get() = workouts.firstOrNull { it.status == WorkoutStatus.IN_PROGRESS }

    companion object {
        val Empty = CloudSnapshot(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

internal data class SnapshotExercise(
    val syncId: String,
    val name: String,
    val category: ExerciseCategory,
)

internal data class SnapshotReferenceMax(
    val syncId: String,
    val category: ExerciseCategory,
    val weightKg: Double,
    val updatedOn: LocalDate,
)

internal data class SnapshotCycle(
    val syncId: String,
    val cycleNumber: Int,
    val status: CycleStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
    /**
     * The maxes this block was calculated from, frozen. Restored as stored and never
     * recomputed from today's reference maxes — that is the difference between restoring a
     * lifter's history and inventing a plausible-looking replacement for it.
     */
    val squatKg: Double,
    val benchPressKg: Double,
    val deadliftKg: Double,
    val createdAt: Instant,
    val program: SnapshotProgram,
)

internal data class SnapshotProgram(
    val syncId: String,
    val name: String,
    val createdAt: Instant,
    val isActive: Boolean,
    /** Where the lifter is in the sequence. Null once the block has run out. */
    val currentWorkoutTemplateSyncId: String?,
    val weeks: List<SnapshotWeek>,
)

internal data class SnapshotWeek(
    val syncId: String,
    val weekNumber: Int,
    val label: String,
    val isDeload: Boolean,
    val workouts: List<SnapshotWorkoutTemplate>,
)

internal data class SnapshotWorkoutTemplate(
    val syncId: String,
    val dayNumber: Int,
    val sequenceNumber: Int,
    val title: String,
    val exercises: List<SnapshotExerciseTemplate>,
)

internal data class SnapshotExerciseTemplate(
    val syncId: String,
    val exerciseSyncId: String,
    /** Snapshotted alongside the reference, so a renamed movement cannot rewrite an old plan. */
    val exerciseName: String,
    val exerciseCategory: ExerciseCategory,
    val type: ExerciseType,
    val position: Int,
    val plannedSets: List<SnapshotPlannedSet>,
)

internal data class SnapshotPlannedSet(
    val syncId: String,
    val position: Int,
    val weightKg: Double?,
    val reps: Int?,
    val rpeMin: Double?,
    val rpeMax: Double?,
)

internal data class SnapshotWorkout(
    val syncId: String,
    /** Provenance. Null when the plan it came from is gone; the snapshot below still stands. */
    val templateSyncId: String?,
    val cycleSyncId: String?,
    val weekNumber: Int,
    val dayNumber: Int,
    val title: String,
    val isDeload: Boolean,
    val status: WorkoutStatus,
    val date: LocalDate,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val totalVolumeKg: Double?,
    val notes: String?,
    val exercises: List<SnapshotExerciseLog>,
)

internal data class SnapshotExerciseLog(
    val syncId: String,
    val exerciseSyncId: String?,
    val exerciseName: String,
    val exerciseCategory: ExerciseCategory,
    val type: ExerciseType,
    val position: Int,
    val sets: List<SnapshotSetLog>,
)

/**
 * Planned and actual side by side, exactly as the local schema and the API both hold them.
 *
 * A restore that dropped the planned columns and rebuilt them from the current program would
 * silently rewrite what a lifter was actually asked to do on a day two years ago.
 */
internal data class SnapshotSetLog(
    val syncId: String,
    val position: Int,
    val plannedWeightKg: Double?,
    val plannedReps: Int?,
    val plannedRpeMin: Double?,
    val plannedRpeMax: Double?,
    val actualWeightKg: Double?,
    val actualReps: Int?,
    val actualRpe: Double?,
    val completed: Boolean,
    val notes: String?,
)
