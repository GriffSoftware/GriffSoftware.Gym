package com.griffgym.domain.model

/**
 * How a template prescribes the load for a set.
 *
 * Main-lift work is relative — "the top single is 89.29% of your squat max" — so the same
 * template produces a different, correct plan for every lifter. Accessory work is either
 * prescribed with a fixed load or left to the lifter, which is why the load on a
 * [TemplateEntry] is nullable.
 */
sealed interface TemplateLoad {

    /** A share of the lifter's reference max for [category]. */
    data class OfReferenceMax(val category: ExerciseCategory, val percent: Double) : TemplateLoad {
        init {
            require(percent.isFinite() && percent > 0.0) {
                "A percentage of a reference max must be positive, was $percent"
            }
        }
    }

    /** A load the plan states outright, independent of any reference max. */
    data class Fixed(val weight: Weight) : TemplateLoad
}

/** A movement the template needs to exist in the exercise catalogue. */
data class TemplateExercise(
    val name: String,
    val category: ExerciseCategory,
)

/** One prescription line: "3 x 3 @ 83.33% of squat, RPE 7". */
data class TemplateEntry(
    val exerciseName: String,
    val type: ExerciseType,
    val sets: Int,
    val reps: Int,
    val load: TemplateLoad?,
    val targetRpe: RpeTarget,
) {
    init {
        require(sets >= 1) { "'$exerciseName' must prescribe at least one set, was $sets" }
        require(reps >= 1) { "'$exerciseName' must prescribe at least one rep, was $reps" }
    }
}

data class TemplateDay(
    val dayNumber: Int,
    val title: String,
    val entries: List<TemplateEntry>,
)

data class TemplateWeek(
    val weekNumber: Int,
    val label: String,
    val isDeload: Boolean,
    val days: List<TemplateDay>,
)

/**
 * The canonical, lifter-independent shape of a training block.
 *
 * A template holds no kilograms for the main lifts, only percentages; concrete loads are
 * produced by [TrainingBlockGenerator] once the lifter's reference maxes are known.
 */
data class TrainingTemplate(
    val name: String,
    val exercises: List<TemplateExercise>,
    val weeks: List<TemplateWeek>,
) {
    private val entries: List<TemplateEntry>
        get() = weeks.flatMap { week -> week.days.flatMap { it.entries } }

    /** The lifts the generator cannot produce a plan without. */
    val requiredReferenceMaxes: Set<ExerciseCategory> =
        entries.mapNotNull { (it.load as? TemplateLoad.OfReferenceMax)?.category }.toSet()

    init {
        val known = exercises.map { it.name }.toSet()
        val unknown = entries.map { it.exerciseName }.toSet() - known
        require(unknown.isEmpty()) {
            "Template '$name' prescribes exercises missing from its catalogue: $unknown"
        }
    }
}
