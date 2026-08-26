package com.griffgym.application.onboarding

import com.griffgym.domain.repository.OnboardingRepository
import com.griffgym.domain.repository.ReferenceMaxRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import javax.inject.Inject

/** What the app should show once startup has resolved. */
sealed interface AppInitializationState {

    /** A genuinely fresh install: no flag, no training data. First run setup is required. */
    data object NeedsOnboarding : AppInitializationState

    /** Everything is in place — go straight to the normal app. */
    data object Ready : AppInitializationState
}

/**
 * Decides whether the lifter has to go through first run setup.
 *
 * Two signals are combined on purpose. The stored flag is authoritative, but it did not
 * exist before onboarding shipped, so an installation that already contains training data
 * must never be dragged through setup — that lifter would be asked for maxes they already
 * entered, on top of a program they are halfway through. Detecting that case once and
 * writing the flag is the migration: nothing in the database is touched.
 *
 * The Room checks are ordered cheapest-first and short circuit, so a fresh install pays for
 * three trivial existence queries and an upgraded install usually pays for one.
 */
class GetAppInitializationStateUseCase @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val referenceMaxRepository: ReferenceMaxRepository,
    private val trainingProgramRepository: TrainingProgramRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
) {

    suspend operator fun invoke(): AppInitializationState {
        if (onboardingRepository.isOnboardingCompleted()) return AppInitializationState.Ready

        if (!hasExistingTrainingData()) return AppInitializationState.NeedsOnboarding

        onboardingRepository.markOnboardingCompleted()
        return AppInitializationState.Ready
    }

    private suspend fun hasExistingTrainingData(): Boolean =
        referenceMaxRepository.hasAnyReferenceMax() ||
            trainingProgramRepository.hasProgram() ||
            workoutSessionRepository.hasAnySession()
}
