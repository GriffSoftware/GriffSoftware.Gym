package com.griffgym.application.onboarding

import com.griffgym.application.cycle.StartTrainingCycleUseCase
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.Weight
import com.griffgym.domain.repository.OnboardingRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import javax.inject.Inject

/**
 * Turns the three numbers the lifter entered during setup into a usable app: cycle 1, the
 * training block that belongs to it, and the reference maxes it was calculated from.
 *
 * Setup does not have a creation path of its own. It calls [StartTrainingCycleUseCase], the
 * same one "START CYCLE N+1" calls, so the very first block and the seventh are written by
 * identical code.
 *
 * There are only two writes, and the split is deliberate:
 *
 *  1. the cycle, its program *and* the maxes, in one database transaction, so they are
 *     either all there or all absent;
 *  2. the "setup is done" flag, which lives outside the training database.
 *
 * The plan and the maxes cannot be sequenced safely, because a process death between them
 * is indistinguishable on the next launch from an installation that predates setup: the app
 * would see a program, conclude setup was already done and send the lifter to a plan whose
 * numbers they can never see or edit. Committing them together removes that window.
 *
 * If step 1 fails nothing was written and the next launch offers setup again. If step 2
 * fails, the lifter has a complete plan with its maxes on file — genuinely the same shape as
 * a pre-setup installation — and [GetAppInitializationStateUseCase] repairs the flag.
 */
class CompleteOnboardingUseCase @Inject constructor(
    private val startTrainingCycle: StartTrainingCycleUseCase,
    private val trainingProgramRepository: TrainingProgramRepository,
    private val onboardingRepository: OnboardingRepository,
) {

    suspend operator fun invoke(referenceMaxes: Map<ExerciseCategory, Weight>): Result<Unit> =
        runCatching {
            val validated = validate(referenceMaxes).getOrThrow()

            // Setup never replaces a plan that is already there. The UI cannot reach this
            // point twice, but a plan is a lifter's whole block and is not worth risking on
            // that assumption alone.
            if (!trainingProgramRepository.hasProgram()) {
                startTrainingCycle(ReferenceMaxSnapshot.of(validated)).getOrThrow()
            }

            onboardingRepository.markOnboardingCompleted()
        }

    private fun validate(
        referenceMaxes: Map<ExerciseCategory, Weight>,
    ): Result<Map<ExerciseCategory, Weight>> {
        val usable = referenceMaxes.filterValues { !it.isZero }
        val missing = REQUIRED_LIFTS - usable.keys
        return if (missing.isEmpty()) {
            Result.success(usable)
        } else {
            Result.failure(IllegalArgumentException("Missing a reference max for $missing"))
        }
    }

    private companion object {
        /** A block cannot be generated without every lift it prescribes a percentage of. */
        val REQUIRED_LIFTS: Set<ExerciseCategory> =
            StrengthBlockTemplate.template.requiredReferenceMaxes
    }
}
