package com.griffgym.domain.model

/**
 * "Blok IV — Siła", transcribed from the `Plan treningowy - siła.xlsx` sheet and rewritten
 * as percentages of the lifter's reference maxes.
 *
 * The sheet was originally written for SQ 210 / DL 225 / BP 170 kg and prescribed absolute
 * kilograms. Those kilograms are expressed here as percentages of exactly those maxes, to
 * two decimal places — enough precision that generating weeks one to five with the baseline
 * maxes reproduces the sheet set for set, while any other lifter gets the same relative
 * plan. `GenerateTrainingBlockUseCaseTest` guards both properties.
 *
 * Week six is the one place the block departs from the sheet. The sheet's deload was a
 * handful of loosely related percentages left over from its absolute kilograms; it is now a
 * flat [DELOAD_PERCENT] of every main lift, so the week reads as what it is — a deliberate,
 * uniform drop in intensity to shed fatigue before the next cycle.
 *
 * Prescribed RPE follows the legend on the sheet: TOP is the heavy work of the day,
 * back-off consolidates it, volume accumulates and light/deload exist for technique and
 * recovery. Accessory work is not driven by a reference max and keeps its literal
 * prescription.
 */
object StrengthBlockTemplate {

    const val PROGRAM_NAME = "Blok IV — Siła"

    /**
     * Every main lift in the deload week is half of the cycle's reference max. One number
     * for the whole week, so "am I recovering or training?" is never ambiguous.
     */
    const val DELOAD_PERCENT: Double = 50.0

    const val SQUAT = "Przysiad"
    const val DEADLIFT = "Martwy ciąg"
    const val BENCH = "Ławka"

    /** The maxes the sheet was written for. Kept for documentation and regression testing. */
    val baselineReferenceMaxes: Map<ExerciseCategory, Weight> = mapOf(
        ExerciseCategory.SQUAT to Weight.of(210.0),
        ExerciseCategory.DEADLIFT to Weight.of(225.0),
        ExerciseCategory.BENCH_PRESS to Weight.of(170.0),
    )

    private val TOP_RPE = RpeTarget.exact(8.0)
    private val BACK_OFF_RPE = RpeTarget.exact(7.0)
    private val VOLUME_RPE = RpeTarget.range(6.0, 7.0)
    private val LIGHT_RPE = RpeTarget.range(5.0, 6.0)
    private val DELOAD_RPE = RpeTarget.range(5.0, 6.0)
    private val ACCESSORY_RPE = RpeTarget.range(6.0, 7.0)

    private const val DAY_I_TITLE = "Squat Focus / Bench Volume"
    private const val DAY_II_TITLE = "Deadlift Focus / Bench Light"
    private const val DAY_III_TITLE = "Bench Focus / Squat Volume"

    private val exercises: List<TemplateExercise> = listOf(
        TemplateExercise(SQUAT, ExerciseCategory.SQUAT),
        TemplateExercise(DEADLIFT, ExerciseCategory.DEADLIFT),
        TemplateExercise(BENCH, ExerciseCategory.BENCH_PRESS),
        TemplateExercise("Skos Smith", ExerciseCategory.ACCESSORY),
        TemplateExercise("RDL", ExerciseCategory.ACCESSORY),
        TemplateExercise("Triceps Hantla & Wyciąg", ExerciseCategory.ACCESSORY),
        TemplateExercise("Biceps Hantla & Wyciąg", ExerciseCategory.ACCESSORY),
        TemplateExercise("Rozpiętki", ExerciseCategory.ACCESSORY),
        TemplateExercise("Triceps Wyciąg I / II", ExerciseCategory.ACCESSORY),
        TemplateExercise("Biceps Wyciąg I / II", ExerciseCategory.ACCESSORY),
        TemplateExercise("Hamstring Curl", ExerciseCategory.ACCESSORY),
        TemplateExercise("Łydki Smith", ExerciseCategory.ACCESSORY),
    )

    private val categories: Map<String, ExerciseCategory> =
        exercises.associate { it.name to it.category }

    /**
     * Accessory work is prescribed per training day and repeats across the block.
     * The deload week deliberately drops it — that week exists to shed fatigue.
     */
    private val accessoriesByDay: Map<Int, List<TemplateEntry>> = mapOf(
        1 to listOf(
            accessory("Skos Smith", sets = 3, reps = 12),
            accessory("RDL", sets = 2, reps = 8),
            accessory("Triceps Hantla & Wyciąg", sets = 3, reps = 15),
            accessory("Biceps Hantla & Wyciąg", sets = 3, reps = 15),
        ),
        2 to listOf(
            accessory("Rozpiętki", sets = 3, reps = 20),
            accessory("Triceps Wyciąg I / II", sets = 3, reps = 15),
            accessory("Biceps Wyciąg I / II", sets = 2, reps = 15),
        ),
        3 to listOf(
            accessory("Hamstring Curl", sets = 2, reps = 20),
            accessory("Łydki Smith", sets = 2, reps = 20),
        ),
    )

    val template: TrainingTemplate = TrainingTemplate(
        name = PROGRAM_NAME,
        exercises = exercises,
        weeks = listOf(
            week(
                number = 1,
                label = "ACCUMULATION",
                dayI = listOf(
                    top(SQUAT, sets = 1, reps = 3, percent = 89.29),
                    backOff(SQUAT, sets = 3, reps = 3, percent = 83.33),
                    volume(BENCH, sets = 4, reps = 6, percent = 73.53),
                ),
                dayII = listOf(
                    top(DEADLIFT, sets = 1, reps = 3, percent = 88.89),
                    backOff(DEADLIFT, sets = 3, reps = 3, percent = 82.22),
                    light(BENCH, sets = 4, reps = 5, percent = 67.65),
                ),
                dayIII = listOf(
                    top(BENCH, sets = 1, reps = 3, percent = 88.24),
                    backOff(BENCH, sets = 3, reps = 3, percent = 82.35),
                    volume(SQUAT, sets = 4, reps = 5, percent = 71.43),
                ),
            ),
            week(
                number = 2,
                label = "ACCUMULATION",
                dayI = listOf(
                    top(SQUAT, sets = 1, reps = 3, percent = 90.48),
                    backOff(SQUAT, sets = 3, reps = 3, percent = 84.52),
                    volume(BENCH, sets = 4, reps = 6, percent = 73.53),
                ),
                dayII = listOf(
                    top(DEADLIFT, sets = 1, reps = 3, percent = 88.89),
                    backOff(DEADLIFT, sets = 3, reps = 3, percent = 82.22),
                    light(BENCH, sets = 4, reps = 5, percent = 69.12),
                ),
                dayIII = listOf(
                    top(BENCH, sets = 1, reps = 3, percent = 88.24),
                    backOff(BENCH, sets = 3, reps = 3, percent = 82.35),
                    volume(SQUAT, sets = 4, reps = 5, percent = 72.62),
                ),
            ),
            week(
                number = 3,
                label = "INTENSIFICATION",
                dayI = listOf(
                    top(SQUAT, sets = 1, reps = 3, percent = 91.67),
                    backOff(SQUAT, sets = 3, reps = 3, percent = 85.71),
                    volume(BENCH, sets = 4, reps = 6, percent = 75.00),
                ),
                dayII = listOf(
                    top(DEADLIFT, sets = 1, reps = 3, percent = 91.11),
                    backOff(DEADLIFT, sets = 3, reps = 3, percent = 84.44),
                    light(BENCH, sets = 4, reps = 5, percent = 69.12),
                ),
                dayIII = listOf(
                    top(BENCH, sets = 1, reps = 3, percent = 89.71),
                    backOff(BENCH, sets = 3, reps = 3, percent = 83.82),
                    volume(SQUAT, sets = 4, reps = 5, percent = 73.81),
                ),
            ),
            week(
                number = 4,
                label = "INTENSIFICATION",
                dayI = listOf(
                    top(SQUAT, sets = 1, reps = 2, percent = 92.86),
                    backOff(SQUAT, sets = 3, reps = 2, percent = 86.90),
                    volume(BENCH, sets = 4, reps = 5, percent = 76.47),
                ),
                dayII = listOf(
                    top(DEADLIFT, sets = 1, reps = 2, percent = 93.33),
                    backOff(DEADLIFT, sets = 3, reps = 2, percent = 86.67),
                    light(BENCH, sets = 3, reps = 5, percent = 70.59),
                ),
                dayIII = listOf(
                    top(BENCH, sets = 1, reps = 2, percent = 91.18),
                    backOff(BENCH, sets = 3, reps = 2, percent = 85.29),
                    volume(SQUAT, sets = 4, reps = 4, percent = 75.00),
                ),
            ),
            week(
                number = 5,
                label = "PEAK",
                dayI = listOf(
                    top(SQUAT, sets = 1, reps = 1, percent = 95.24),
                    backOff(SQUAT, sets = 2, reps = 2, percent = 88.10),
                    volume(BENCH, sets = 3, reps = 5, percent = 77.94),
                ),
                dayII = listOf(
                    top(DEADLIFT, sets = 1, reps = 1, percent = 95.56),
                    backOff(DEADLIFT, sets = 2, reps = 2, percent = 88.89),
                    light(BENCH, sets = 3, reps = 4, percent = 70.59),
                ),
                dayIII = listOf(
                    top(BENCH, sets = 2, reps = 2, percent = 95.59),
                    backOff(BENCH, sets = 2, reps = 2, percent = 86.76),
                    volume(SQUAT, sets = 3, reps = 4, percent = 76.19),
                ),
            ),
            // Built outside week() on purpose: the deload week carries no accessory work,
            // because the point of it is to arrive at the next cycle fresh.
            TemplateWeek(
                weekNumber = 6,
                label = "DELOAD",
                isDeload = true,
                days = listOf(
                    TemplateDay(
                        dayNumber = 1,
                        title = "Deload — Squat / Bench",
                        entries = listOf(
                            deload(SQUAT, sets = 3, reps = 3),
                            deload(BENCH, sets = 3, reps = 5),
                        ),
                    ),
                    TemplateDay(
                        dayNumber = 2,
                        title = "Deload — Deadlift / Bench",
                        entries = listOf(
                            deload(DEADLIFT, sets = 3, reps = 3),
                            deload(BENCH, sets = 3, reps = 5),
                        ),
                    ),
                    TemplateDay(
                        dayNumber = 3,
                        title = "Deload — Bench / Squat",
                        entries = listOf(
                            deload(BENCH, sets = 3, reps = 3),
                            deload(SQUAT, sets = 3, reps = 3),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun week(
        number: Int,
        label: String,
        dayI: List<TemplateEntry>,
        dayII: List<TemplateEntry>,
        dayIII: List<TemplateEntry>,
    ) = TemplateWeek(
        weekNumber = number,
        label = label,
        isDeload = false,
        days = listOf(
            TemplateDay(1, DAY_I_TITLE, dayI + accessoriesByDay.getValue(1)),
            TemplateDay(2, DAY_II_TITLE, dayII + accessoriesByDay.getValue(2)),
            TemplateDay(3, DAY_III_TITLE, dayIII + accessoriesByDay.getValue(3)),
        ),
    )

    private fun top(exercise: String, sets: Int, reps: Int, percent: Double) =
        mainLift(exercise, ExerciseType.TOP, sets, reps, percent, TOP_RPE)

    private fun backOff(exercise: String, sets: Int, reps: Int, percent: Double) =
        mainLift(exercise, ExerciseType.BACK_OFF, sets, reps, percent, BACK_OFF_RPE)

    private fun volume(exercise: String, sets: Int, reps: Int, percent: Double) =
        mainLift(exercise, ExerciseType.VOLUME, sets, reps, percent, VOLUME_RPE)

    private fun light(exercise: String, sets: Int, reps: Int, percent: Double) =
        mainLift(exercise, ExerciseType.LIGHT, sets, reps, percent, LIGHT_RPE)

    private fun deload(exercise: String, sets: Int, reps: Int) =
        mainLift(exercise, ExerciseType.DELOAD, sets, reps, DELOAD_PERCENT, DELOAD_RPE)

    private fun mainLift(
        exercise: String,
        type: ExerciseType,
        sets: Int,
        reps: Int,
        percent: Double,
        rpe: RpeTarget,
    ): TemplateEntry {
        val category = requireNotNull(categories[exercise]) { "Unknown exercise '$exercise'" }
        require(category.isBigThree) { "'$exercise' has no reference max to take $percent% of" }
        return TemplateEntry(
            exerciseName = exercise,
            type = type,
            sets = sets,
            reps = reps,
            load = TemplateLoad.OfReferenceMax(category, percent),
            targetRpe = rpe,
        )
    }

    private fun accessory(exercise: String, sets: Int, reps: Int) = TemplateEntry(
        exerciseName = exercise,
        type = ExerciseType.ACCESSORY,
        sets = sets,
        reps = reps,
        load = null,
        targetRpe = ACCESSORY_RPE,
    )
}
