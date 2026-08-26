package com.griffgym.application

import com.griffgym.application.onboarding.GenerateTrainingBlockUseCase
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.GeneratedExercise
import com.griffgym.domain.model.GeneratedProgram
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WeightRoundingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The block used to be a table of hard-coded kilograms. It is now percentages of the
 * lifter's maxes, and the load-bearing promise of that change is this: generating it with
 * the maxes the sheet was written for reproduces the sheet, set for set.
 */
class GenerateTrainingBlockUseCaseTest {

    private val generate = GenerateTrainingBlockUseCase()

    @Test
    fun `the baseline maxes reproduce week one day one exactly as it was prescribed`() {
        val program = generate(BASELINE)
        val day = program.day(week = 1, day = 1)

        assertEquals("Przysiad TOP 1x3x187.5", day[0].describe())
        assertEquals("Przysiad BACK_OFF 3x3x175", day[1].describe())
        assertEquals("Ławka VOLUME 4x6x125", day[2].describe())
    }

    @Test
    fun `the baseline maxes reproduce the whole six week block`() {
        val program = generate(BASELINE)

        assertEquals(6, program.weeks.size)
        assertEquals(18, program.workouts.size)

        BASELINE_PLAN.forEach { (position, expected) ->
            val (week, day) = position
            assertEquals(
                "week $week day $day",
                expected,
                program.day(week, day).map { it.describe() },
            )
        }
    }

    @Test
    fun `the deload week is marked and drops accessory work`() {
        val program = generate(BASELINE)
        val deload = program.weeks.single { it.weekNumber == 6 }

        assertTrue(deload.isDeload)
        assertTrue(program.weeks.filter { it.weekNumber != 6 }.none { it.isDeload })
        assertTrue(deload.days.all { day -> day.exercises.all { it.exerciseName != "RDL" } })
        // The whole point of the week is to arrive at the next cycle fresh.
        assertTrue(
            deload.days.all { day -> day.exercises.none { it.category == ExerciseCategory.ACCESSORY } },
        )
    }

    @Test
    fun `every deload load is exactly half the max it came from`() {
        val program = generate(BASELINE)
        val deload = program.weeks.single { it.weekNumber == 6 }

        deload.days.flatMap { it.exercises }.forEach { exercise ->
            val max = BASELINE.getValue(exercise.category)
            assertEquals(
                "${exercise.exerciseName} deload",
                WeightRoundingPolicy.round(max.kilograms * 0.5),
                exercise.sets.first().weight,
            )
        }
    }

    @Test
    fun `a deload load that lands between plates rounds up, not down`() {
        // 152.5 x 50% = 76.25 kg, exactly halfway between 75 and 77.5. Ties round up, so
        // the recovery week never drifts quietly under what it prescribes.
        val program = generate(
            BASELINE + (ExerciseCategory.BENCH_PRESS to Weight.of(152.5)),
        )
        val benchDeload = program
            .day(week = 6, day = 1)
            .single { it.category == ExerciseCategory.BENCH_PRESS }

        assertEquals(77.5, benchDeload.sets.first().weight!!.kilograms, 1e-9)
    }

    @Test
    fun `the deload week is three days of two main lifts and nothing else`() {
        val deload = generate(BASELINE).weeks.single { it.weekNumber == 6 }

        assertEquals(3, deload.days.size)
        assertEquals(listOf(2, 2, 2), deload.days.map { it.exercises.size })
        assertEquals(
            listOf(
                listOf(ExerciseCategory.SQUAT, ExerciseCategory.BENCH_PRESS),
                listOf(ExerciseCategory.DEADLIFT, ExerciseCategory.BENCH_PRESS),
                listOf(ExerciseCategory.BENCH_PRESS, ExerciseCategory.SQUAT),
            ),
            deload.days.map { day -> day.exercises.map { it.category } },
        )
        assertEquals(
            listOf(listOf(3, 3), listOf(3, 3), listOf(3, 3)),
            deload.days.map { day -> day.exercises.map { it.sets.size } },
        )
        assertEquals(
            listOf(listOf(3, 5), listOf(3, 5), listOf(3, 3)),
            deload.days.map { day -> day.exercises.map { it.sets.first().reps } },
        )
    }

    @Test
    fun `a different lifter gets the same plan scaled to their own maxes`() {
        val program = generate(
            mapOf(
                ExerciseCategory.SQUAT to Weight.of(180.0),
                ExerciseCategory.BENCH_PRESS to Weight.of(140.0),
                ExerciseCategory.DEADLIFT to Weight.of(200.0),
            ),
        )
        val week1 = program.day(week = 1, day = 1)

        // 180 x 89.29% = 160.7 kg, snapped down to a bar you can actually load.
        assertEquals("Przysiad TOP 1x3x160", week1[0].describe())
        assertEquals("Przysiad BACK_OFF 3x3x150", week1[1].describe())
        assertEquals("Ławka VOLUME 4x6x102.5", week1[2].describe())

        val day2 = program.day(week = 1, day = 2)
        assertEquals("Martwy ciąg TOP 1x3x177.5", day2[0].describe())
    }

    @Test
    fun `every prescribed load can be loaded on a bar`() {
        val program = generate(
            mapOf(
                ExerciseCategory.SQUAT to Weight.of(183.0),
                ExerciseCategory.BENCH_PRESS to Weight.of(141.0),
                ExerciseCategory.DEADLIFT to Weight.of(207.0),
            ),
        )

        program.mainLiftWeights().forEach { weight ->
            assertEquals(
                "$weight is not a multiple of ${WeightRoundingPolicy.INCREMENT_KG}",
                0.0,
                weight.kilograms % WeightRoundingPolicy.INCREMENT_KG,
                1e-9,
            )
        }
    }

    @Test
    fun `accessory work keeps its literal prescription and stays unloaded`() {
        val program = generate(BASELINE)
        val accessory = program.day(week = 1, day = 1).first { it.exerciseName == "Skos Smith" }

        assertEquals(ExerciseCategory.ACCESSORY, accessory.category)
        assertEquals(3, accessory.sets.size)
        assertNull(accessory.sets.first().weight)
        assertEquals(12, accessory.sets.first().reps)
        assertEquals("6-7", accessory.sets.first().targetRpe!!.format())
    }

    @Test
    fun `scaling a max does not change what the plan asks of the lifter`() {
        val prescribed = generate(BASELINE).day(week = 3, day = 1).first()
        val doubled = generate(BASELINE.mapValues { Weight.of(it.value.kilograms * 2) })
            .day(week = 3, day = 1)
            .first()

        assertEquals(prescribed.sets.size, doubled.sets.size)
        assertEquals(prescribed.sets.first().reps, doubled.sets.first().reps)
        assertEquals(385.0, doubled.sets.first().weight!!.kilograms, 1.25)
    }

    @Test
    fun `a block cannot be generated without every lift it is built on`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            generate(BASELINE - ExerciseCategory.DEADLIFT)
        }
        assertTrue(error.message!!.contains("DEADLIFT"))
    }

    @Test
    fun `a zero max counts as no max at all`() {
        assertThrows(IllegalArgumentException::class.java) {
            generate(BASELINE + (ExerciseCategory.SQUAT to Weight.ZERO))
        }
    }

    @Test
    fun `the plan declares the exercises it needs, with their categories`() {
        val required = generate(BASELINE).requiredExercises

        assertEquals(12, required.size)
        assertEquals(
            ExerciseCategory.SQUAT,
            required.first { it.name == "Przysiad" }.category,
        )
        assertEquals(
            ExerciseCategory.ACCESSORY,
            required.first { it.name == "Łydki Smith" }.category,
        )
    }

    private fun GeneratedProgram.day(week: Int, day: Int): List<GeneratedExercise> =
        weeks.single { it.weekNumber == week }.days.single { it.dayNumber == day }.exercises

    private fun GeneratedProgram.mainLiftWeights(): List<Weight> =
        workouts.flatMap { it.exercises }.flatMap { it.sets }.mapNotNull { it.weight }

    /** "Przysiad TOP 1x3x187.5" — the sheet's own shorthand, so a failure reads like the plan. */
    private fun GeneratedExercise.describe(): String {
        val first = sets.first()
        return buildString {
            append(exerciseName).append(' ').append(type.name).append(' ')
            append(sets.size).append('x').append(first.reps)
            first.weight?.let { append('x').append(it.format()) }
        }
    }

    private companion object {
        val BASELINE: Map<ExerciseCategory, Weight> = StrengthBlockTemplate.baselineReferenceMaxes

        val ACCESSORIES_DAY_I = listOf(
            "Skos Smith ACCESSORY 3x12",
            "RDL ACCESSORY 2x8",
            "Triceps Hantla & Wyciąg ACCESSORY 3x15",
            "Biceps Hantla & Wyciąg ACCESSORY 3x15",
        )
        val ACCESSORIES_DAY_II = listOf(
            "Rozpiętki ACCESSORY 3x20",
            "Triceps Wyciąg I / II ACCESSORY 3x15",
            "Biceps Wyciąg I / II ACCESSORY 2x15",
        )
        val ACCESSORIES_DAY_III = listOf(
            "Hamstring Curl ACCESSORY 2x20",
            "Łydki Smith ACCESSORY 2x20",
        )

        /** Transcribed from the "Blok IV" sheet, at SQ 210 / DL 225 / BP 170. */
        val BASELINE_PLAN: Map<Pair<Int, Int>, List<String>> = mapOf(
            (1 to 1) to listOf(
                "Przysiad TOP 1x3x187.5",
                "Przysiad BACK_OFF 3x3x175",
                "Ławka VOLUME 4x6x125",
            ) + ACCESSORIES_DAY_I,
            (1 to 2) to listOf(
                "Martwy ciąg TOP 1x3x200",
                "Martwy ciąg BACK_OFF 3x3x185",
                "Ławka LIGHT 4x5x115",
            ) + ACCESSORIES_DAY_II,
            (1 to 3) to listOf(
                "Ławka TOP 1x3x150",
                "Ławka BACK_OFF 3x3x140",
                "Przysiad VOLUME 4x5x150",
            ) + ACCESSORIES_DAY_III,

            (2 to 1) to listOf(
                "Przysiad TOP 1x3x190",
                "Przysiad BACK_OFF 3x3x177.5",
                "Ławka VOLUME 4x6x125",
            ) + ACCESSORIES_DAY_I,
            (2 to 2) to listOf(
                "Martwy ciąg TOP 1x3x200",
                "Martwy ciąg BACK_OFF 3x3x185",
                "Ławka LIGHT 4x5x117.5",
            ) + ACCESSORIES_DAY_II,
            (2 to 3) to listOf(
                "Ławka TOP 1x3x150",
                "Ławka BACK_OFF 3x3x140",
                "Przysiad VOLUME 4x5x152.5",
            ) + ACCESSORIES_DAY_III,

            (3 to 1) to listOf(
                "Przysiad TOP 1x3x192.5",
                "Przysiad BACK_OFF 3x3x180",
                "Ławka VOLUME 4x6x127.5",
            ) + ACCESSORIES_DAY_I,
            (3 to 2) to listOf(
                "Martwy ciąg TOP 1x3x205",
                "Martwy ciąg BACK_OFF 3x3x190",
                "Ławka LIGHT 4x5x117.5",
            ) + ACCESSORIES_DAY_II,
            (3 to 3) to listOf(
                "Ławka TOP 1x3x152.5",
                "Ławka BACK_OFF 3x3x142.5",
                "Przysiad VOLUME 4x5x155",
            ) + ACCESSORIES_DAY_III,

            (4 to 1) to listOf(
                "Przysiad TOP 1x2x195",
                "Przysiad BACK_OFF 3x2x182.5",
                "Ławka VOLUME 4x5x130",
            ) + ACCESSORIES_DAY_I,
            (4 to 2) to listOf(
                "Martwy ciąg TOP 1x2x210",
                "Martwy ciąg BACK_OFF 3x2x195",
                "Ławka LIGHT 3x5x120",
            ) + ACCESSORIES_DAY_II,
            (4 to 3) to listOf(
                "Ławka TOP 1x2x155",
                "Ławka BACK_OFF 3x2x145",
                "Przysiad VOLUME 4x4x157.5",
            ) + ACCESSORIES_DAY_III,

            (5 to 1) to listOf(
                "Przysiad TOP 1x1x200",
                "Przysiad BACK_OFF 2x2x185",
                "Ławka VOLUME 3x5x132.5",
            ) + ACCESSORIES_DAY_I,
            (5 to 2) to listOf(
                "Martwy ciąg TOP 1x1x215",
                "Martwy ciąg BACK_OFF 2x2x200",
                "Ławka LIGHT 3x4x120",
            ) + ACCESSORIES_DAY_II,
            (5 to 3) to listOf(
                "Ławka TOP 2x2x162.5",
                "Ławka BACK_OFF 2x2x147.5",
                "Przysiad VOLUME 3x4x160",
            ) + ACCESSORIES_DAY_III,

            // The one place the block departs from the sheet: a flat half of every max,
            // rather than the sheet's leftover assortment of percentages.
            (6 to 1) to listOf(
                "Przysiad DELOAD 3x3x105",
                "Ławka DELOAD 3x5x85",
            ),
            (6 to 2) to listOf(
                "Martwy ciąg DELOAD 3x3x112.5",
                "Ławka DELOAD 3x5x85",
            ),
            (6 to 3) to listOf(
                "Ławka DELOAD 3x3x85",
                "Przysiad DELOAD 3x3x105",
            ),
        )
    }
}
