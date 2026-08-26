package com.griffgym.application.cycle

import com.griffgym.application.onboarding.GenerateTrainingBlockUseCase
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.repository.TrainingCycleRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The one way a training cycle ever comes into existence.
 *
 * First-run setup and "start cycle N+1" both go through here, so cycle 1 and cycle 7 are
 * created by exactly the same code: generate the block from [referenceMaxes], then hand plan
 * and maxes to the repository to be written as a single transaction. Nothing else in the app
 * may create a program.
 *
 * Nothing is started automatically. The lifter ends up looking at "Cycle N, Week 1 Day I,
 * READY", the same as any other unstarted workout — no session is opened on their behalf.
 */
class StartTrainingCycleUseCase @Inject constructor(
    private val generateTrainingBlock: GenerateTrainingBlockUseCase,
    private val cycleRepository: TrainingCycleRepository,
    private val programRepository: TrainingProgramRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(referenceMaxes: ReferenceMaxSnapshot): Result<TrainingCycle> =
        runCatching {
            // A cycle is six weeks of plan. Never replace one the lifter still has work left
            // in — and, just as importantly, this makes a double tap on "START CYCLE N+1"
            // harmless: the first call leaves a pointer at week 1 day I, which the second
            // call sees and refuses.
            check(programRepository.getCurrentWorkoutTemplate() == null) {
                "The current cycle still has workouts left to train"
            }

            cycleRepository.startCycle(
                program = generateTrainingBlock(referenceMaxes.byCategory),
                referenceMaxes = referenceMaxes,
                date = LocalDate.now(clock),
                startedAt = clock.instant(),
            )
        }
}
