package com.griffgym.application

import com.griffgym.application.cycle.CalculateNextCycleReferenceMaxUseCase
import com.griffgym.application.cycle.GetCurrentReferenceMaxSnapshotUseCase
import com.griffgym.domain.model.CycleProgressionDecision
import com.griffgym.domain.model.DefaultCycleProgressionPolicy
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.domain.model.ReferenceMaxChange
import com.griffgym.domain.model.ReferenceMaxDelta
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.Weight
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CalculateNextCycleReferenceMaxUseCaseTest {

    private val calculate = CalculateNextCycleReferenceMaxUseCase()

    private val current = ReferenceMaxSnapshot(
        squat = Weight.of(200.0),
        benchPress = Weight.of(150.0),
        deadlift = Weight.of(220.0),
    )

    @Test
    fun `the defaults move two hundred, one fifty and two twenty up a step`() {
        val progression = calculate(current, DefaultCycleProgressionPolicy.defaultDecision())
            .getOrThrow()

        assertEquals(205.0, progression.squat.next.kilograms, 1e-9)
        assertEquals(152.5, progression.benchPress.next.kilograms, 1e-9)
        assertEquals(225.0, progression.deadlift.next.kilograms, 1e-9)
    }

    @Test
    fun `a mixed decision applies each lift's own answer`() {
        val progression = calculate(
            current,
            CycleProgressionDecision(
                squat = DefaultCycleProgressionPolicy.defaultChange(ExerciseCategory.SQUAT),
                benchPress = ReferenceMaxChange.Keep,
                deadlift = ReferenceMaxChange.Custom(ReferenceMaxDelta.of(-5.0)),
            ),
        ).getOrThrow()

        assertEquals(
            ReferenceMaxSnapshot(Weight.of(205.0), Weight.of(150.0), Weight.of(215.0)),
            progression.next,
        )
    }

    @Test
    fun `a custom increase is taken exactly as typed`() {
        val progression = calculate(current, allLifts(ReferenceMaxDelta.of(7.5))).getOrThrow()

        assertEquals(207.5, progression.squat.next.kilograms, 1e-9)
        assertEquals(157.5, progression.benchPress.next.kilograms, 1e-9)
        assertEquals(227.5, progression.deadlift.next.kilograms, 1e-9)
    }

    @Test
    fun `a result at or below zero is a failure, not a lighter block`() {
        assertTrue(calculate(current, allLifts(ReferenceMaxDelta.of(-200.0))).isFailure)
        assertTrue(calculate(current, allLifts(ReferenceMaxDelta.of(-500.0))).isFailure)
    }

    @Test
    fun `one impossible lift fails the whole decision`() {
        val result = calculate(
            current,
            CycleProgressionDecision(
                squat = ReferenceMaxChange.Keep,
                benchPress = ReferenceMaxChange.Keep,
                deadlift = ReferenceMaxChange.Custom(ReferenceMaxDelta.of(-220.0)),
            ),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `today's maxes win over the snapshot the block was built from`() = runTest {
        // A max corrected on Home halfway through a block is the number the lifter meant.
        val referenceMaxes = FakeReferenceMaxRepository(
            listOf(
                ReferenceMax(ExerciseCategory.SQUAT, Weight.of(215.0), TODAY),
                ReferenceMax(ExerciseCategory.BENCH_PRESS, Weight.of(150.0), TODAY),
                ReferenceMax(ExerciseCategory.DEADLIFT, Weight.of(220.0), TODAY),
            ),
        )
        val programs = FakeTrainingProgramRepository.empty()
        val cycles = FakeTrainingCycleRepository(programs, referenceMaxes)
        cycles.startCycle(
            program = generatedProgram(),
            referenceMaxes = current,
            date = TODAY,
            startedAt = STARTED_AT,
        )
        // Starting the cycle wrote its snapshot into the live table; put the correction back.
        referenceMaxes.updateReferenceMax(ExerciseCategory.SQUAT, Weight.of(215.0), TODAY)

        val snapshot = GetCurrentReferenceMaxSnapshotUseCase(referenceMaxes, cycles)()
            .getOrThrow()

        assertEquals(215.0, snapshot.squat.kilograms, 1e-9)
        // And the cycle's own record of what it was built from is untouched.
        assertEquals(200.0, cycles.getCurrentCycle()!!.referenceMaxes.squat.kilograms, 1e-9)
    }

    @Test
    fun `a missing live max falls back to the cycle's snapshot rather than dead-ending`() = runTest {
        val referenceMaxes = FakeReferenceMaxRepository(initial = emptyList())
        val programs = FakeTrainingProgramRepository.empty()
        val cycles = FakeTrainingCycleRepository(programs, referenceMaxes)
        cycles.startCycle(
            program = generatedProgram(),
            referenceMaxes = current,
            date = TODAY,
            startedAt = STARTED_AT,
        )
        referenceMaxes.restore(emptyList())

        val snapshot = GetCurrentReferenceMaxSnapshotUseCase(referenceMaxes, cycles)()
            .getOrThrow()

        assertEquals(current, snapshot)
    }

    @Test
    fun `no maxes anywhere is a failure the screen can report`() = runTest {
        val referenceMaxes = FakeReferenceMaxRepository(initial = emptyList())
        val programs = FakeTrainingProgramRepository.empty()
        val cycles = FakeTrainingCycleRepository(programs, referenceMaxes)

        assertTrue(GetCurrentReferenceMaxSnapshotUseCase(referenceMaxes, cycles)().isFailure)
    }

    private fun allLifts(delta: ReferenceMaxDelta) = CycleProgressionDecision(
        squat = ReferenceMaxChange.Custom(delta),
        benchPress = ReferenceMaxChange.Custom(delta),
        deadlift = ReferenceMaxChange.Custom(delta),
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 4, 1)
        val STARTED_AT: Instant = Instant.parse("2026-04-01T09:00:00Z")
    }
}
