package com.griffgym.domain.model

/**
 * Turns a percentage-based [TrainingTemplate] into a concrete block for one lifter.
 *
 * Every main-lift load is `referenceMax x percent`, snapped onto the plate increment by
 * [WeightRoundingPolicy]; accessory prescriptions are copied through untouched. The result
 * is a plain value — nothing is persisted here, and nothing about Room or Android is
 * visible to this calculation, which is what makes the whole plan unit testable.
 *
 * Feeding the block's own baseline maxes back in reproduces the original sheet exactly.
 */
object TrainingBlockGenerator {

    fun generate(
        template: TrainingTemplate,
        referenceMaxes: Map<ExerciseCategory, Weight>,
    ): GeneratedProgram {
        val usable = referenceMaxes.filterValues { !it.isZero }
        val missing = template.requiredReferenceMaxes - usable.keys
        require(missing.isEmpty()) {
            "Cannot generate '${template.name}' without a reference max for $missing"
        }

        val categories = template.exercises.associate { it.name to it.category }

        return GeneratedProgram(
            name = template.name,
            weeks = template.weeks.map { week ->
                GeneratedWeek(
                    weekNumber = week.weekNumber,
                    label = week.label,
                    isDeload = week.isDeload,
                    days = week.days.map { day -> day.generate(usable, categories) },
                )
            },
        )
    }

    private fun TemplateDay.generate(
        referenceMaxes: Map<ExerciseCategory, Weight>,
        categories: Map<String, ExerciseCategory>,
    ) = GeneratedWorkout(
        dayNumber = dayNumber,
        title = title,
        exercises = entries.mapIndexed { index, entry ->
            val weight = entry.load?.resolve(referenceMaxes)
            GeneratedExercise(
                exerciseName = entry.exerciseName,
                category = categories.getValue(entry.exerciseName),
                type = entry.type,
                position = index + 1,
                // "3x3x175" becomes three concrete sets: a session snapshots each one
                // individually, and the lifter may deviate on any of them.
                sets = (1..entry.sets).map { position ->
                    GeneratedSet(
                        position = position,
                        weight = weight,
                        reps = entry.reps,
                        targetRpe = entry.targetRpe,
                    )
                },
            )
        },
    )

    private fun TemplateLoad.resolve(referenceMaxes: Map<ExerciseCategory, Weight>): Weight =
        when (this) {
            is TemplateLoad.Fixed -> weight
            is TemplateLoad.OfReferenceMax -> {
                val max = requireNotNull(referenceMaxes[category]) { "No reference max for $category" }
                WeightRoundingPolicy.round(max.kilograms * percent / 100.0)
            }
        }
}
