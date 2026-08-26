package com.griffgym.application

import com.griffgym.application.cycle.StartTrainingCycleUseCase
import com.griffgym.application.onboarding.CompleteOnboardingUseCase
import com.griffgym.application.onboarding.GenerateTrainingBlockUseCase
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.Weight
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CompleteOnboardingUseCaseTest {

    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:30:00Z"), ZoneOffset.UTC)

    private val maxes = mapOf(
        ExerciseCategory.SQUAT to Weight.of(210.0),
        ExerciseCategory.BENCH_PRESS to Weight.of(170.0),
        ExerciseCategory.DEADLIFT to Weight.of(225.0),
    )

    @Test
    fun `builds the block and stores the maxes it was built from`() = runTest {
        val fixture = Fixture()

        val result = fixture.useCase(maxes)

        assertTrue(result.isSuccess)
        assertEquals(1, fixture.cycles.startedPrograms.size)
        assertEquals("Blok IV — Siła", fixture.cycles.startedPrograms.single().name)

        assertEquals(maxes, fixture.storedMaxes())
        assertEquals(
            LocalDate.of(2026, 3, 4),
            fixture.referenceMaxes.getReferenceMax(ExerciseCategory.SQUAT)!!.updatedOn,
        )
        assertTrue(fixture.onboarding.isOnboardingCompleted())
    }

    @Test
    fun `the generated block is the lifter's own, not the sheet's`() = runTest {
        val fixture = Fixture()

        fixture.useCase(
            mapOf(
                ExerciseCategory.SQUAT to Weight.of(180.0),
                ExerciseCategory.BENCH_PRESS to Weight.of(140.0),
                ExerciseCategory.DEADLIFT to Weight.of(200.0),
            ),
        )

        val topSet = fixture.cycles.startedPrograms.single()
            .weeks.first().days.first().exercises.first().sets.first()
        assertEquals(160.0, topSet.weight!!.kilograms, 1e-9)
    }

    @Test
    fun `a failed write leaves setup unfinished so the lifter can try again`() = runTest {
        val fixture = Fixture()
        fixture.cycles.failOnStart = true

        val result = fixture.useCase(maxes)

        assertTrue(result.isFailure)
        assertFalse(fixture.onboarding.isOnboardingCompleted())
        assertTrue(fixture.referenceMaxes.observeReferenceMaxes().first().isEmpty())
    }

    @Test
    fun `the plan and the maxes are written as one call, not one after the other`() = runTest {
        val fixture = Fixture()

        assertTrue(fixture.useCase(maxes).isSuccess)

        // Nothing may reach the reference max store except through program creation: a
        // separate follow-up write is exactly the window a process death would fall into.
        assertEquals(0, fixture.referenceMaxes.standaloneWrites)
        assertEquals(maxes, fixture.storedMaxes())
    }

    @Test
    fun `maxes that fail to persist take the plan down with them`() = runTest {
        val fixture = Fixture()
        // The plan is already staged when persistence gives out on the maxes — the exact
        // window that used to leave a program behind with no numbers to go with it.
        fixture.referenceMaxes.failOnWrite = true

        val result = fixture.useCase(maxes)

        assertTrue(result.isFailure)
        assertTrue(fixture.cycles.startedPrograms.isEmpty())
        assertFalse(fixture.programs.hasProgram())
        assertTrue(fixture.referenceMaxes.observeReferenceMaxes().first().isEmpty())
        assertFalse(fixture.onboarding.isOnboardingCompleted())
    }

    @Test
    fun `setup can be finished on a later attempt after the maxes failed to persist`() = runTest {
        val fixture = Fixture()
        fixture.referenceMaxes.failOnWrite = true
        assertTrue(fixture.useCase(maxes).isFailure)

        fixture.referenceMaxes.failOnWrite = false
        val retry = fixture.useCase(maxes)

        // The rolled back attempt must not have left a program behind, or the retry would
        // skip creation and strand the lifter on a plan with no maxes.
        assertTrue(retry.isSuccess)
        assertEquals(1, fixture.cycles.startedPrograms.size)
        assertEquals(maxes, fixture.storedMaxes())
        assertTrue(fixture.onboarding.isOnboardingCompleted())
    }

    @Test
    fun `a missing lift is refused before anything is written`() = runTest {
        val fixture = Fixture()

        val result = fixture.useCase(maxes - ExerciseCategory.DEADLIFT)

        assertTrue(result.isFailure)
        assertTrue(fixture.cycles.startedPrograms.isEmpty())
        assertFalse(fixture.onboarding.isOnboardingCompleted())
    }

    @Test
    fun `a zero max is refused before anything is written`() = runTest {
        val fixture = Fixture()

        val result = fixture.useCase(maxes + (ExerciseCategory.BENCH_PRESS to Weight.ZERO))

        assertTrue(result.isFailure)
        assertTrue(fixture.cycles.startedPrograms.isEmpty())
        assertFalse(fixture.onboarding.isOnboardingCompleted())
    }

    @Test
    fun `running twice never leaves the lifter with two blocks`() = runTest {
        val fixture = Fixture()

        assertTrue(fixture.useCase(maxes).isSuccess)
        assertTrue(fixture.useCase(maxes).isSuccess)

        assertEquals(1, fixture.cycles.startedPrograms.size)
    }

    /**
     * Setup goes through the very same [StartTrainingCycleUseCase] that "START CYCLE N+1"
     * does, which is what this fixture wires up: there is no second creation path to test.
     */
    private inner class Fixture {
        val referenceMaxes = FakeReferenceMaxRepository(initial = emptyList())
        val programs = FakeTrainingProgramRepository.empty()
        val cycles = FakeTrainingCycleRepository(programs, referenceMaxes)
        val onboarding = FakeOnboardingRepository()

        suspend fun storedMaxes(): Map<ExerciseCategory, Weight> =
            referenceMaxes.observeReferenceMaxes().first().associate { it.category to it.weight }

        val useCase = CompleteOnboardingUseCase(
            startTrainingCycle = StartTrainingCycleUseCase(
                generateTrainingBlock = GenerateTrainingBlockUseCase(),
                cycleRepository = cycles,
                programRepository = programs,
                clock = clock,
            ),
            trainingProgramRepository = programs,
            onboardingRepository = onboarding,
        )
    }
}
