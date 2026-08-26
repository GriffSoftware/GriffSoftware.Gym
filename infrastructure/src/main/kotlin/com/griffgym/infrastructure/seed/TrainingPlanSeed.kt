package com.griffgym.infrastructure.seed

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.RpeTarget

internal data class SeedExercise(
    val name: String,
    val category: ExerciseCategory,
)

internal data class SeedEntry(
    val exercise: String,
    val type: ExerciseType,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?,
    val rpe: RpeTarget,
)

internal data class SeedDay(
    val dayNumber: Int,
    val title: String,
    val entries: List<SeedEntry>,
)

internal data class SeedWeek(
    val weekNumber: Int,
    val label: String,
    val isDeload: Boolean,
    val days: List<SeedDay>,
)

/**
 * The lifter's actual block, transcribed from the "Blok IV" sheet of
 * `Plan treningowy - siła.xlsx`.
 *
 * Prescribed RPE follows the legend on that sheet: TOP is the heavy single of the day,
 * back-off consolidates it, volume accumulates work and light/deload exist for technique
 * and recovery.
 */
internal object TrainingPlanSeed {

    const val PROGRAM_NAME = "Blok IV — Siła"

    const val SQUAT = "Przysiad"
    const val DEADLIFT = "Martwy ciąg"
    const val BENCH = "Ławka"

    private val TOP_RPE = RpeTarget.exact(8.0)
    private val BACK_OFF_RPE = RpeTarget.exact(7.0)
    private val VOLUME_RPE = RpeTarget.range(6.0, 7.0)
    private val LIGHT_RPE = RpeTarget.range(5.0, 6.0)
    private val DELOAD_RPE = RpeTarget.range(5.0, 6.0)
    private val ACCESSORY_RPE = RpeTarget.range(6.0, 7.0)

    private const val DAY_I_TITLE = "Squat Focus / Bench Volume"
    private const val DAY_II_TITLE = "Deadlift Focus / Bench Light"
    private const val DAY_III_TITLE = "Bench Focus / Squat Volume"

    val exercises: List<SeedExercise> = listOf(
        SeedExercise(SQUAT, ExerciseCategory.SQUAT),
        SeedExercise(DEADLIFT, ExerciseCategory.DEADLIFT),
        SeedExercise(BENCH, ExerciseCategory.BENCH_PRESS),
        SeedExercise("Skos Smith", ExerciseCategory.ACCESSORY),
        SeedExercise("RDL", ExerciseCategory.ACCESSORY),
        SeedExercise("Triceps Hantla & Wyciąg", ExerciseCategory.ACCESSORY),
        SeedExercise("Biceps Hantla & Wyciąg", ExerciseCategory.ACCESSORY),
        SeedExercise("Rozpiętki", ExerciseCategory.ACCESSORY),
        SeedExercise("Triceps Wyciąg I / II", ExerciseCategory.ACCESSORY),
        SeedExercise("Biceps Wyciąg I / II", ExerciseCategory.ACCESSORY),
        SeedExercise("Hamstring Curl", ExerciseCategory.ACCESSORY),
        SeedExercise("Łydki Smith", ExerciseCategory.ACCESSORY),
    )

    /**
     * Accessory work is prescribed per training day and repeats across the block.
     * The deload week deliberately drops it — that week exists to shed fatigue.
     */
    private val accessoriesByDay: Map<Int, List<SeedEntry>> = mapOf(
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

    val weeks: List<SeedWeek> = listOf(
        week(
            number = 1,
            label = "ACCUMULATION",
            dayI = listOf(
                top(SQUAT, 1, 3, 187.5),
                backOff(SQUAT, 3, 3, 175.0),
                volume(BENCH, 4, 6, 125.0),
            ),
            dayII = listOf(
                top(DEADLIFT, 1, 3, 200.0),
                backOff(DEADLIFT, 3, 3, 185.0),
                light(BENCH, 4, 5, 115.0),
            ),
            dayIII = listOf(
                top(BENCH, 1, 3, 150.0),
                backOff(BENCH, 3, 3, 140.0),
                volume(SQUAT, 4, 5, 150.0),
            ),
        ),
        week(
            number = 2,
            label = "ACCUMULATION",
            dayI = listOf(
                top(SQUAT, 1, 3, 190.0),
                backOff(SQUAT, 3, 3, 177.5),
                volume(BENCH, 4, 6, 125.0),
            ),
            dayII = listOf(
                top(DEADLIFT, 1, 3, 200.0),
                backOff(DEADLIFT, 3, 3, 185.0),
                light(BENCH, 4, 5, 117.5),
            ),
            dayIII = listOf(
                top(BENCH, 1, 3, 150.0),
                backOff(BENCH, 3, 3, 140.0),
                volume(SQUAT, 4, 5, 152.5),
            ),
        ),
        week(
            number = 3,
            label = "INTENSIFICATION",
            dayI = listOf(
                top(SQUAT, 1, 3, 192.5),
                backOff(SQUAT, 3, 3, 180.0),
                volume(BENCH, 4, 6, 127.5),
            ),
            dayII = listOf(
                top(DEADLIFT, 1, 3, 205.0),
                backOff(DEADLIFT, 3, 3, 190.0),
                light(BENCH, 4, 5, 117.5),
            ),
            dayIII = listOf(
                top(BENCH, 1, 3, 152.5),
                backOff(BENCH, 3, 3, 142.5),
                volume(SQUAT, 4, 5, 155.0),
            ),
        ),
        week(
            number = 4,
            label = "INTENSIFICATION",
            dayI = listOf(
                top(SQUAT, 1, 2, 195.0),
                backOff(SQUAT, 3, 2, 182.5),
                volume(BENCH, 4, 5, 130.0),
            ),
            dayII = listOf(
                top(DEADLIFT, 1, 2, 210.0),
                backOff(DEADLIFT, 3, 2, 195.0),
                light(BENCH, 3, 5, 120.0),
            ),
            dayIII = listOf(
                top(BENCH, 1, 2, 155.0),
                backOff(BENCH, 3, 2, 145.0),
                volume(SQUAT, 4, 4, 157.5),
            ),
        ),
        week(
            number = 5,
            label = "PEAK",
            dayI = listOf(
                top(SQUAT, 1, 1, 200.0),
                backOff(SQUAT, 2, 2, 185.0),
                volume(BENCH, 3, 5, 132.5),
            ),
            dayII = listOf(
                top(DEADLIFT, 1, 1, 215.0),
                backOff(DEADLIFT, 2, 2, 200.0),
                light(BENCH, 3, 4, 120.0),
            ),
            dayIII = listOf(
                top(BENCH, 2, 2, 162.5),
                backOff(BENCH, 2, 2, 147.5),
                volume(SQUAT, 3, 4, 160.0),
            ),
        ),
        SeedWeek(
            weekNumber = 6,
            label = "DELOAD",
            isDeload = true,
            days = listOf(
                SeedDay(
                    dayNumber = 1,
                    title = "Deload — Squat / Bench",
                    entries = listOf(
                        deload(SQUAT, 3, 3, 150.0),
                        deload(BENCH, 3, 5, 110.0),
                    ),
                ),
                SeedDay(
                    dayNumber = 2,
                    title = "Deload — Deadlift / Bench",
                    entries = listOf(
                        deload(DEADLIFT, 3, 3, 170.0),
                        deload(BENCH, 3, 5, 105.0),
                    ),
                ),
                SeedDay(
                    dayNumber = 3,
                    title = "Deload — Bench / Squat",
                    entries = listOf(
                        deload(BENCH, 3, 3, 120.0),
                        deload(SQUAT, 3, 3, 140.0),
                    ),
                ),
            ),
        ),
    )

    /** Reference maxes as declared on the sheet: SQ 210, DL 225, BP 170. */
    val referenceMaxes: Map<ExerciseCategory, Double> = mapOf(
        ExerciseCategory.SQUAT to 210.0,
        ExerciseCategory.DEADLIFT to 225.0,
        ExerciseCategory.BENCH_PRESS to 170.0,
    )

    private fun week(
        number: Int,
        label: String,
        dayI: List<SeedEntry>,
        dayII: List<SeedEntry>,
        dayIII: List<SeedEntry>,
    ) = SeedWeek(
        weekNumber = number,
        label = label,
        isDeload = false,
        days = listOf(
            SeedDay(1, DAY_I_TITLE, dayI + accessoriesByDay.getValue(1)),
            SeedDay(2, DAY_II_TITLE, dayII + accessoriesByDay.getValue(2)),
            SeedDay(3, DAY_III_TITLE, dayIII + accessoriesByDay.getValue(3)),
        ),
    )

    private fun top(exercise: String, sets: Int, reps: Int, weight: Double) =
        SeedEntry(exercise, ExerciseType.TOP, sets, reps, weight, TOP_RPE)

    private fun backOff(exercise: String, sets: Int, reps: Int, weight: Double) =
        SeedEntry(exercise, ExerciseType.BACK_OFF, sets, reps, weight, BACK_OFF_RPE)

    private fun volume(exercise: String, sets: Int, reps: Int, weight: Double) =
        SeedEntry(exercise, ExerciseType.VOLUME, sets, reps, weight, VOLUME_RPE)

    private fun light(exercise: String, sets: Int, reps: Int, weight: Double) =
        SeedEntry(exercise, ExerciseType.LIGHT, sets, reps, weight, LIGHT_RPE)

    private fun deload(exercise: String, sets: Int, reps: Int, weight: Double) =
        SeedEntry(exercise, ExerciseType.DELOAD, sets, reps, weight, DELOAD_RPE)

    private fun accessory(exercise: String, sets: Int, reps: Int) =
        SeedEntry(exercise, ExerciseType.ACCESSORY, sets, reps, weightKg = null, rpe = ACCESSORY_RPE)
}
