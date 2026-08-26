package com.griffgym.domain.model

import java.time.Instant

/**
 * Where a cycle is in its life.
 *
 * There is deliberately no "ready" state. "The cycle exists but nothing has been trained
 * yet" is already expressed one level down, by [CurrentWorkout.Planned] on the first unit —
 * duplicating it here would give the app two sources of truth for the same fact.
 */
enum class CycleStatus {
    /** The lifter is training it: it still has at least one unit left. */
    ACTIVE,

    /** Its last scheduled unit was completed. Never entered because time passed. */
    COMPLETED;

    val isActive: Boolean get() = this == ACTIVE
    val isCompleted: Boolean get() = this == COMPLETED
}

/**
 * The three planning numbers a cycle was generated from, frozen at the moment it started.
 *
 * This is not the same thing as the `reference_max` table. That table holds what the lifter
 * believes their maxes are *today* and is theirs to edit at any point; this is the historical
 * record of what a particular block was actually calculated from, and never changes again.
 *
 * Zero is permitted. A snapshot is a record, not an input: the only way a zero can appear is
 * an installation upgraded from before cycles existed whose reference maxes were somehow
 * missing, and refusing to read that row would be worse than showing it honestly.
 * [TrainingBlockGenerator] rejects zero maxes on its own, so no plan can be built from one.
 */
data class ReferenceMaxSnapshot(
    val squat: Weight,
    val benchPress: Weight,
    val deadlift: Weight,
) {
    operator fun get(category: ExerciseCategory): Weight? = when (category) {
        ExerciseCategory.SQUAT -> squat
        ExerciseCategory.BENCH_PRESS -> benchPress
        ExerciseCategory.DEADLIFT -> deadlift
        ExerciseCategory.ACCESSORY -> null
    }

    /**
     * The three lifts in [ExerciseCategory.bigThree] order — squat, deadlift, bench — which
     * is both the order the block opens day I, II and III with and the order every list of
     * lifts in the app is drawn in. One ordering, so nothing has to be re-sorted downstream.
     */
    val byCategory: Map<ExerciseCategory, Weight>
        get() = ExerciseCategory.bigThree.associateWith { category ->
            requireNotNull(get(category)) { "No reference max for $category" }
        }

    companion object {
        /** Fails loudly rather than generating a block from an incomplete set of maxes. */
        fun of(maxes: Map<ExerciseCategory, Weight>): ReferenceMaxSnapshot {
            val missing = ExerciseCategory.bigThree.filter { maxes[it] == null }
            require(missing.isEmpty()) { "A cycle needs a reference max for every lift, missing $missing" }
            return ReferenceMaxSnapshot(
                squat = maxes.getValue(ExerciseCategory.SQUAT),
                benchPress = maxes.getValue(ExerciseCategory.BENCH_PRESS),
                deadlift = maxes.getValue(ExerciseCategory.DEADLIFT),
            )
        }
    }
}

/**
 * One six week run through the block.
 *
 * A cycle owns exactly one generated [TrainingProgram] and the maxes that program was built
 * from. Cycles are numbered from one and never renumbered, so "cycle 3" means the same thing
 * forever, and a completed cycle is immutable history in exactly the way a finished session
 * is.
 */
data class TrainingCycle(
    val id: Long,
    val cycleNumber: Int,
    val status: CycleStatus,
    val startedAt: Instant,
    /** Set exactly when the last scheduled workout was completed. */
    val completedAt: Instant?,
    val referenceMaxes: ReferenceMaxSnapshot,
    val createdAt: Instant,
) {
    init {
        require(cycleNumber >= 1) { "Cycles are numbered from one, got $cycleNumber" }
        require(status.isCompleted == (completedAt != null)) {
            "A cycle is completed exactly when it has a completion time, got $status / $completedAt"
        }
    }

    val isActive: Boolean get() = status.isActive
    val isCompleted: Boolean get() = status.isCompleted

    /** "CYCLE 3" — the label the whole app refers to a cycle by. */
    val label: String get() = "CYCLE $cycleNumber"
}

/** How far through one week of a cycle the lifter got. */
data class CycleWeekProgress(
    val weekNumber: Int,
    val label: String,
    val isDeload: Boolean,
    val plannedWorkouts: Int,
    val completedWorkouts: Int,
) {
    init {
        require(plannedWorkouts >= 0 && completedWorkouts >= 0) {
            "Week $weekNumber cannot have a negative workout count"
        }
    }

    val isComplete: Boolean get() = plannedWorkouts > 0 && completedWorkouts >= plannedWorkouts
    val isStarted: Boolean get() = completedWorkouts > 0
}

/**
 * A cycle together with what actually happened inside it.
 *
 * Progress is counted from completed sessions rather than tracked as its own state, so it
 * cannot drift away from the training log.
 */
data class TrainingCycleSummary(
    val cycle: TrainingCycle,
    val weeks: List<CycleWeekProgress>,
) {
    val weekCount: Int get() = weeks.size

    val plannedWorkouts: Int get() = weeks.sumOf { it.plannedWorkouts }

    val completedWorkouts: Int get() = weeks.sumOf { it.completedWorkouts }

    val completedWeeks: Int get() = weeks.count { it.isComplete }

    /**
     * The week the lifter is in: the first one that is not finished. Null once the whole
     * cycle is done, which is the same moment the program runs out of units.
     */
    val currentWeekNumber: Int? get() = weeks.firstOrNull { !it.isComplete }?.weekNumber
}
