package com.griffgym.domain.model

/**
 * A concrete training block calculated for one lifter, ready to be persisted.
 *
 * This is deliberately *not* [TrainingProgram]: that model describes a program that already
 * lives in the database and therefore carries identifiers. A generated program has no ids
 * yet — it is the plan as computed, before anything has been written.
 */
data class GeneratedProgram(
    val name: String,
    val weeks: List<GeneratedWeek>,
) {
    val workouts: List<GeneratedWorkout>
        get() = weeks.sortedBy { it.weekNumber }.flatMap { week -> week.days.sortedBy { it.dayNumber } }

    /**
     * Every movement the plan refers to, in first-appearance order. Carried on the plan so
     * that persisting it never depends on the exercise catalogue having been seeded first.
     */
    val requiredExercises: List<TemplateExercise>
        get() = workouts
            .flatMap { day -> day.exercises }
            .distinctBy { it.exerciseName }
            .map { TemplateExercise(it.exerciseName, it.category) }
}

data class GeneratedWeek(
    val weekNumber: Int,
    val label: String,
    val isDeload: Boolean,
    val days: List<GeneratedWorkout>,
)

data class GeneratedWorkout(
    val dayNumber: Int,
    val title: String,
    val exercises: List<GeneratedExercise>,
)

data class GeneratedExercise(
    val exerciseName: String,
    val category: ExerciseCategory,
    val type: ExerciseType,
    val position: Int,
    val sets: List<GeneratedSet>,
)

/**
 * One prescribed set. [weight] is null for work the plan leaves to the lifter, exactly as
 * `planned_set.weightKg` is nullable in the database.
 */
data class GeneratedSet(
    val position: Int,
    val weight: Weight?,
    val reps: Int?,
    val targetRpe: RpeTarget?,
)
