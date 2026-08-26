package com.griffgym.domain

import com.griffgym.domain.model.CycleComparison
import com.griffgym.domain.model.CycleProgression
import com.griffgym.domain.model.CycleProgressionDecision
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.DefaultCycleProgressionPolicy
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.LiftProgression
import com.griffgym.domain.model.ReferenceMaxChange
import com.griffgym.domain.model.ReferenceMaxDelta
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.Weight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The arithmetic behind "where does the next cycle start?".
 *
 * These are the numbers a lifter trains on for six weeks, so every case that can change one
 * of them — a default, a hold, a number they typed, a number they typed with a minus in
 * front of it — is pinned here rather than left to the screen that collects them.
 */
class CycleProgressionTest {

    private val current = ReferenceMaxSnapshot(
        squat = Weight.of(200.0),
        benchPress = Weight.of(150.0),
        deadlift = Weight.of(220.0),
    )

    @Test
    fun `the default step up is five, two and a half, five`() {
        assertEquals(5.0, defaultKg(ExerciseCategory.SQUAT), 1e-9)
        // Upper body strength moves in half steps, so the bench does too.
        assertEquals(2.5, defaultKg(ExerciseCategory.BENCH_PRESS), 1e-9)
        assertEquals(5.0, defaultKg(ExerciseCategory.DEADLIFT), 1e-9)
    }

    @Test
    fun `an accessory has no reference max to progress`() {
        assertThrows(IllegalArgumentException::class.java) {
            DefaultCycleProgressionPolicy.defaultIncrease(ExerciseCategory.ACCESSORY)
        }
    }

    @Test
    fun `the defaults applied to two hundred, one fifty and two twenty`() {
        val progression = CycleProgression.from(
            current,
            DefaultCycleProgressionPolicy.defaultDecision(),
        )

        assertEquals(
            ReferenceMaxSnapshot(Weight.of(205.0), Weight.of(152.5), Weight.of(225.0)),
            progression.next,
        )
        // The cycle that produced them is left exactly as it was.
        assertEquals(current, progression.current)
    }

    @Test
    fun `keeping a max leaves it where it is`() {
        val progression = CycleProgression.from(
            current,
            CycleProgressionDecision(
                squat = ReferenceMaxChange.Keep,
                benchPress = ReferenceMaxChange.Keep,
                deadlift = ReferenceMaxChange.Keep,
            ),
        )

        assertEquals(current, progression.next)
        assertTrue(progression.lifts.all { it.change.delta.isZero })
    }

    @Test
    fun `the three lifts are decided independently of each other`() {
        // Added to the squat, held the bench, dropped the deadlift after a tweak: three
        // perfectly ordinary decisions, not an inconsistency.
        val progression = CycleProgression.from(
            current,
            CycleProgressionDecision(
                squat = DefaultCycleProgressionPolicy.defaultChange(ExerciseCategory.SQUAT),
                benchPress = ReferenceMaxChange.Keep,
                deadlift = ReferenceMaxChange.Custom(ReferenceMaxDelta.of(-10.0)),
            ),
        )

        assertEquals(
            ReferenceMaxSnapshot(Weight.of(205.0), Weight.of(150.0), Weight.of(210.0)),
            progression.next,
        )
    }

    @Test
    fun `a custom increase in half kilograms lands on a half kilogram`() {
        val progression = LiftProgression(
            category = ExerciseCategory.BENCH_PRESS,
            current = Weight.of(147.5),
            change = ReferenceMaxChange.Custom(ReferenceMaxDelta.of(2.5)),
        )

        assertEquals(150.0, progression.next.kilograms, 1e-9)
    }

    @Test
    fun `a custom decrease lowers the max`() {
        // Illness, a break, a bad block: lowering a reference max is a supported decision,
        // which is why the change is signed and is not a Weight.
        val progression = LiftProgression(
            category = ExerciseCategory.SQUAT,
            current = Weight.of(200.0),
            change = ReferenceMaxChange.Custom(ReferenceMaxDelta.of(-12.5)),
        )

        assertEquals(187.5, progression.next.kilograms, 1e-9)
    }

    @Test
    fun `a change that would zero a max is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            LiftProgression(
                category = ExerciseCategory.SQUAT,
                current = Weight.of(200.0),
                change = ReferenceMaxChange.Custom(ReferenceMaxDelta.of(-200.0)),
            )
        }
    }

    @Test
    fun `a change that would take a max below zero is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            LiftProgression(
                category = ExerciseCategory.DEADLIFT,
                current = Weight.of(220.0),
                change = ReferenceMaxChange.Custom(ReferenceMaxDelta.of(-500.0)),
            )
        }
    }

    @Test
    fun `an increase has to actually increase`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReferenceMaxChange.Increase(ReferenceMaxDelta.of(-5.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReferenceMaxChange.Increase(ReferenceMaxDelta.NONE)
        }
    }

    @Test
    fun `a change has to be a real number`() {
        assertThrows(IllegalArgumentException::class.java) { ReferenceMaxDelta.of(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) {
            ReferenceMaxDelta.of(Double.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReferenceMaxDelta.of(Double.NEGATIVE_INFINITY)
        }
    }

    @Test
    fun `a typed change accepts a comma, a sign and surrounding space`() {
        assertEquals(2.5, ReferenceMaxDelta.parse("2,5")!!.kilograms, 1e-9)
        assertEquals(2.5, ReferenceMaxDelta.parse(" +2.5 ")!!.kilograms, 1e-9)
        assertEquals(-2.5, ReferenceMaxDelta.parse("-2,5")!!.kilograms, 1e-9)
        assertEquals(0.0, ReferenceMaxDelta.parse("0")!!.kilograms, 1e-9)
    }

    @Test
    fun `an unfinished typed change is not a number yet`() {
        assertNull(ReferenceMaxDelta.parse(""))
        assertNull(ReferenceMaxDelta.parse("-"))
        assertNull(ReferenceMaxDelta.parse("kg"))
        assertNull(ReferenceMaxDelta.parse("NaN"))
    }

    @Test
    fun `a change reads with its sign, because the sign is the point`() {
        assertEquals("+5", ReferenceMaxDelta.of(5.0).format())
        assertEquals("-2.5", ReferenceMaxDelta.of(-2.5).format())
        assertEquals("0", ReferenceMaxDelta.NONE.format())
    }

    @Test
    fun `a snapshot needs every lift`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReferenceMaxSnapshot.of(mapOf(ExerciseCategory.SQUAT to Weight.of(200.0)))
        }
    }

    @Test
    fun `comparing two cycles reads their snapshots, not the decision behind them`() {
        val comparison = CycleComparison(
            previous = cycle(number = 1, maxes = current),
            current = cycle(
                number = 2,
                maxes = ReferenceMaxSnapshot(Weight.of(205.0), Weight.of(150.0), Weight.of(210.0)),
            ),
        )

        assertEquals(
            listOf("+5", "-10", "0"),
            comparison.lifts.map { it.delta.format() },
        )
        assertEquals(
            listOf(ExerciseCategory.SQUAT, ExerciseCategory.DEADLIFT, ExerciseCategory.BENCH_PRESS),
            comparison.lifts.map { it.category },
        )
        assertTrue(comparison.lifts.single { it.category == ExerciseCategory.BENCH_PRESS }.delta.isZero)
    }

    @Test
    fun `a cycle is completed exactly when it has a completion time`() {
        assertThrows(IllegalArgumentException::class.java) {
            cycle(number = 1, maxes = current).copy(status = CycleStatus.COMPLETED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            cycle(number = 1, maxes = current).copy(completedAt = Instant.parse("2026-04-01T10:00:00Z"))
        }
        assertFalse(cycle(number = 1, maxes = current).isCompleted)
    }

    @Test
    fun `cycles are numbered from one`() {
        assertThrows(IllegalArgumentException::class.java) { cycle(number = 0, maxes = current) }
    }

    private fun defaultKg(category: ExerciseCategory): Double =
        DefaultCycleProgressionPolicy.defaultIncrease(category).kilograms

    private fun cycle(number: Int, maxes: ReferenceMaxSnapshot) = TrainingCycle(
        id = number.toLong(),
        cycleNumber = number,
        status = CycleStatus.ACTIVE,
        startedAt = Instant.parse("2026-01-01T10:00:00Z"),
        completedAt = null,
        referenceMaxes = maxes,
        createdAt = Instant.parse("2026-01-01T10:00:00Z"),
    )
}
