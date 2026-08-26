package com.griffgym.application

import com.griffgym.application.onboarding.AppInitializationState
import com.griffgym.application.onboarding.GetAppInitializationStateUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that decides whether a lifter is asked to set the app up. Getting it wrong in
 * either direction is bad: a fresh install with no plan, or an existing lifter being asked
 * for maxes on top of a block they are three weeks into.
 */
class GetAppInitializationStateUseCaseTest {

    @Test
    fun `a fresh install is sent through setup`() = runTest {
        val onboarding = FakeOnboardingRepository(completed = false)
        val useCase = useCase(onboarding = onboarding, hasData = false)

        assertEquals(AppInitializationState.NeedsOnboarding, useCase())
        assertEquals(0, onboarding.markCalls)
    }

    @Test
    fun `an installation upgraded from before onboarding is left alone`() = runTest {
        val onboarding = FakeOnboardingRepository(completed = false)
        val useCase = useCase(onboarding = onboarding, hasData = true)

        assertEquals(AppInitializationState.Ready, useCase())
        // The flag is repaired once, silently, so the check is not repeated every launch.
        assertEquals(1, onboarding.markCalls)
    }

    @Test
    fun `existing reference maxes alone are enough to recognise an old installation`() = runTest {
        val useCase = GetAppInitializationStateUseCase(
            onboardingRepository = FakeOnboardingRepository(completed = false),
            referenceMaxRepository = FakeReferenceMaxRepository(),
            trainingProgramRepository = FakeTrainingProgramRepository(programExists = false),
            workoutSessionRepository = FakeWorkoutSessionRepository(),
        )

        assertEquals(AppInitializationState.Ready, useCase())
    }

    @Test
    fun `an existing program alone is enough to recognise an old installation`() = runTest {
        val useCase = GetAppInitializationStateUseCase(
            onboardingRepository = FakeOnboardingRepository(completed = false),
            referenceMaxRepository = FakeReferenceMaxRepository(initial = emptyList()),
            trainingProgramRepository = FakeTrainingProgramRepository(programExists = true),
            workoutSessionRepository = FakeWorkoutSessionRepository(),
        )

        assertEquals(AppInitializationState.Ready, useCase())
    }

    @Test
    fun `logged history alone is enough to recognise an old installation`() = runTest {
        val useCase = GetAppInitializationStateUseCase(
            onboardingRepository = FakeOnboardingRepository(completed = false),
            referenceMaxRepository = FakeReferenceMaxRepository(initial = emptyList()),
            trainingProgramRepository = FakeTrainingProgramRepository(programExists = false),
            workoutSessionRepository = FakeWorkoutSessionRepository(listOf(session(id = 1))),
        )

        assertEquals(AppInitializationState.Ready, useCase())
    }

    @Test
    fun `a completed flag settles it regardless of what is in the database`() = runTest {
        val onboarding = FakeOnboardingRepository(completed = true)
        val useCase = useCase(onboarding = onboarding, hasData = false)

        assertEquals(AppInitializationState.Ready, useCase())
        // Already true: nothing to repair, and no reason to write again.
        assertEquals(0, onboarding.markCalls)
    }

    private fun useCase(
        onboarding: FakeOnboardingRepository,
        hasData: Boolean,
    ) = GetAppInitializationStateUseCase(
        onboardingRepository = onboarding,
        referenceMaxRepository = FakeReferenceMaxRepository(
            initial = if (hasData) FakeReferenceMaxRepository.defaultReferenceMaxes() else emptyList(),
        ),
        trainingProgramRepository = FakeTrainingProgramRepository(programExists = hasData),
        workoutSessionRepository = FakeWorkoutSessionRepository(
            if (hasData) listOf(session(id = 1)) else emptyList(),
        ),
    )
}
