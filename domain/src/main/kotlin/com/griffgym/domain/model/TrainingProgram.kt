package com.griffgym.domain.model

/**
 * A single prescribed set inside a template. Accessory work has no prescribed load,
 * hence the nullable [weight].
 */
data class PlannedSet(
    val id: Long,
    val position: Int,
    val weight: Weight?,
    val reps: Int?,
    val targetRpe: RpeTarget?,
)

data class ExerciseTemplate(
    val id: Long,
    val position: Int,
    val exercise: Exercise,
    val type: ExerciseType,
    val plannedSets: List<PlannedSet>,
) {
    /**
     * "3x3x175" when every planned set shares the same prescription, otherwise `null`
     * because a mixed prescription cannot be collapsed into one line.
     */
    val scheme: SetScheme? = SetScheme.from(plannedSets)
}

/** The `sets x reps x weight` shorthand used throughout the plan. */
data class SetScheme(
    val sets: Int,
    val reps: Int?,
    val weight: Weight?,
    val targetRpe: RpeTarget?,
) {
    fun format(): String = buildString {
        append(sets)
        if (reps != null) append("x").append(reps)
        if (weight != null) append("x").append(weight.format()).append("kg")
    }

    companion object {
        fun from(sets: List<PlannedSet>): SetScheme? {
            if (sets.isEmpty()) return null
            val first = sets.first()
            val uniform = sets.all {
                it.reps == first.reps && it.weight == first.weight && it.targetRpe == first.targetRpe
            }
            return if (uniform) SetScheme(sets.size, first.reps, first.weight, first.targetRpe) else null
        }
    }
}

data class WorkoutTemplate(
    val id: Long,
    val weekId: Long,
    val weekNumber: Int,
    val dayNumber: Int,
    val sequenceNumber: Int,
    val title: String,
    val isDeload: Boolean,
    val exercises: List<ExerciseTemplate>,
) {
    val mainLifts: List<ExerciseTemplate> get() = exercises.filter { it.type.isMainLift }
}

data class TrainingWeek(
    val id: Long,
    val programId: Long,
    val weekNumber: Int,
    val label: String,
    val isDeload: Boolean,
    val workouts: List<WorkoutTemplate>,
)

data class TrainingProgram(
    val id: Long,
    val name: String,
    val weeks: List<TrainingWeek>,
) {
    val workouts: List<WorkoutTemplate>
        get() = weeks.sortedBy { it.weekNumber }.flatMap { week -> week.workouts.sortedBy { it.dayNumber } }
}
