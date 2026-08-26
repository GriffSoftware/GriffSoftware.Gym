package com.griffgym.application

import com.griffgym.application.cycle.CalculateNextCycleReferenceMaxUseCase
import com.griffgym.application.cycle.GetCurrentReferenceMaxSnapshotUseCase
import com.griffgym.application.cycle.StartNextTrainingCycleUseCase
import com.griffgym.application.cycle.StartTrainingCycleUseCase
import com.griffgym.application.onboarding.GenerateTrainingBlockUseCase
import com.griffgym.domain.model.CycleProgressionDecision
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.DefaultCycleProgressionPolicy
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ReferenceMaxChange
import com.griffgym.domain.model.ReferenceMaxDelta
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.Weight
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Starting the next block: the one write that ends a cycle's decision and begins the next.
 *
 * Everything asserted here is about the whole thing landing together — a new cycle, a new
 * plan aimed at week 1 day I, new live maxes and the previous cycle's record untouched.
 */
class StartNextTrainingCycleUseCaseTest {

    private val clock = Clock.fixed(Instant.parse("2026-04-01T09:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.now(clock)

    @Test
    fun `the next cycle is numbered on from the last and starts active`() = runTest {
        val fixture = Fixture()
        fixture.startFirstCycle()

        val cycle = fixture.startNext(DefaultCycleProgressionPolicy.defaultDecision()).getOrThrow()

        assertEquals(2, cycle.cycleNumber)
        assertEquals(CycleStatus.ACTIVE, cycle.status)
        assertNull(cycle.completedAt)
    }

    @Test
    fun `the new cycle is built from the progressed maxes and stores them as its snapshot`() =
        runTest {
            val fixture = Fixture()
            fixture.startFirstCycle()

            val cycle = fixture.startNext(DefaultCycleProgressionPolicy.defaultDecision())
                .getOrThrow()

            assertEquals(
                ReferenceMaxSnapshot(Weight.of(205.0), Weight.of(152.5), Weight.of(225.0)),
                cycle.referenceMaxes,
            )
        }

    @Test
    fun `the live reference maxes move with the new cycle`() = runTest {
        val fixture = Fixture()
        fixture.startFirstCycle()

        fixture.startNext(DefaultCycleProgressionPolicy.defaultDecision()).getOrThrow()

        assertEquals(
            mapOf(
                ExerciseCategory.SQUAT to Weight.of(205.0),
                ExerciseCategory.BENCH_PRESS to Weight.of(152.5),
                ExerciseCategory.DEADLIFT to Weight.of(225.0),
            ),
            fixture.storedMaxes(),
        )
        assertEquals(today, fixture.referenceMaxes.getReferenceMax(ExerciseCategory.SQUAT)!!.updatedOn)
    }

    @Test
    fun `cycle one's snapshot is history and does not move when cycle two is built`() = runTest {
        val fixture = Fixture()
        val first = fixture.startFirstCycle()

        fixture.startNext(DefaultCycleProgressionPolicy.defaultDecision()).getOrThrow()

        val stored = fixture.cycles.getCycle(first.id)!!
        assertEquals(BASELINE, stored.referenceMaxes)
        assertEquals(1, stored.cycleNumber)
    }

    @Test
    fun `starting the next cycle closes the one before it`() = runTest {
        val fixture = Fixture()
        val first = fixture.startFirstCycle()

        fixture.startNext(DefaultCycleProgressionPolicy.defaultDecision()).getOrThrow()

        assertEquals(CycleStatus.COMPLETED, fixture.cycles.getCycle(first.id)!!.status)
    }

    @Test
    fun `the new cycle lands ready at week one day one, with no session started`() = runTest {
        val fixture = Fixture()
        fixture.startFirstCycle()

        fixture.startNext(DefaultCycleProgressionPolicy.defaultDecision()).getOrThrow()

        val current = fixture.programs.getCurrentWorkoutTemplate()!!
        assertEquals(1, current.weekNumber)
        assertEquals(1, current.dayNumber)
        assertNull(fixture.sessions.getActiveSession())
    }

    @Test
    fun `a double tap produces exactly one new cycle`() = runTest {
        val fixture = Fixture()
        fixture.startFirstCycle()

        val first = fixture.startNext(DefaultCycleProgressionPolicy.defaultDecision())
        val second = fixture.startNext(DefaultCycleProgressionPolicy.defaultDecision())

        assertTrue(first.isSuccess)
        // The second press finds a plan with work left in it and refuses, rather than
        // quietly replacing a block the lifter is one workout into.
        assertFalse(second.isSuccess)
        assertEquals(2, fixture.cycles.getCurrentCycle()!!.cycleNumber)
        assertEquals(2, fixture.cycles.startedPrograms.size)
    }

    @Test
    fun `a decision that leaves a lift at zero is refused and nothing is written`() = runTest {
        val fixture = Fixture()
        val first = fixture.startFirstCycle()

        val result = fixture.startNext(
            CycleProgressionDecision(
                squat = ReferenceMaxChange.Keep,
                benchPress = ReferenceMaxChange.Custom(ReferenceMaxDelta.of(-150.0)),
                deadlift = ReferenceMaxChange.Keep,
            ),
        )

        assertTrue(result.isFailure)
        assertEquals(first.id, fixture.cycles.getCurrentCycle()!!.id)
        assertEquals(1, fixture.cycles.startedPrograms.size)
        assertEquals(BASELINE.benchPress, fixture.storedMaxes()[ExerciseCategory.BENCH_PRESS])
    }

    @Test
    fun `keeping every max repeats the block at the same intensity`() = runTest {
        val fixture = Fixture()
        fixture.startFirstCycle()

        val cycle = fixture.startNext(
            CycleProgressionDecision(
                squat = ReferenceMaxChange.Keep,
                benchPress = ReferenceMaxChange.Keep,
                deadlift = ReferenceMaxChange.Keep,
            ),
        ).getOrThrow()

        assertEquals(BASELINE, cycle.referenceMaxes)
        assertEquals(2, cycle.cycleNumber)
    }

    @Test
    fun `a lifter coming back lighter can lower a max`() = runTest {
        val fixture = Fixture()
        fixture.startFirstCycle()

        val cycle = fixture.startNext(
            CycleProgressionDecision(
                squat = ReferenceMaxChange.Custom(ReferenceMaxDelta.of(-20.0)),
                benchPress = ReferenceMaxChange.Custom(ReferenceMaxDelta.of(-12.5)),
                deadlift = ReferenceMaxChange.Keep,
            ),
        ).getOrThrow()

        assertEquals(
            ReferenceMaxSnapshot(Weight.of(180.0), Weight.of(137.5), Weight.of(220.0)),
            cycle.referenceMaxes,
        )
        assertEquals(Weight.of(180.0), fixture.storedMaxes()[ExerciseCategory.SQUAT])
    }

    private inner class Fixture {
        val referenceMaxes = FakeReferenceMaxRepository(initial = emptyList())
        val programs = FakeTrainingProgramRepository.empty()
        val cycles = FakeTrainingCycleRepository(programs, referenceMaxes)
        val sessions = FakeWorkoutSessionRepository()

        private val startTrainingCycle = StartTrainingCycleUseCase(
            generateTrainingBlock = GenerateTrainingBlockUseCase(),
            cycleRepository = cycles,
            programRepository = programs,
            clock = clock,
        )

        private val startNextTrainingCycle = StartNextTrainingCycleUseCase(
            getCurrentReferenceMaxes = GetCurrentReferenceMaxSnapshotUseCase(referenceMaxes, cycles),
            calculateNextReferenceMaxes = CalculateNextCycleReferenceMaxUseCase(),
            startTrainingCycle = startTrainingCycle,
        )

        /** Cycle 1, then trained to exhaustion so the next one is allowed to start. */
        suspend fun startFirstCycle() = startTrainingCycle(BASELINE).getOrThrow().also {
            cycles.completeCurrentCycle(clock.instant())
        }

        suspend fun startNext(decision: CycleProgressionDecision) = startNextTrainingCycle(decision)

        suspend fun storedMaxes(): Map<ExerciseCategory, Weight> =
            ExerciseCategory.bigThree
                .mapNotNull { category -> referenceMaxes.getReferenceMax(category)?.let { category to it.weight } }
                .toMap()
    }

    private companion object {
        val BASELINE = ReferenceMaxSnapshot(
            squat = Weight.of(200.0),
            benchPress = Weight.of(150.0),
            deadlift = Weight.of(220.0),
        )
    }
}
