package com.griffgym.application.cycle

import com.griffgym.domain.model.CycleProgressionDecision
import com.griffgym.domain.model.TrainingCycle
import javax.inject.Inject

/**
 * "START CYCLE N+1": turns the lifter's three progression decisions into the next six week
 * block.
 *
 * Reads their current maxes, applies the decisions, then hands the result to
 * [StartTrainingCycleUseCase] — the same path first-run setup takes. This use case adds the
 * progression step and nothing else; it deliberately owns no persistence of its own.
 *
 * It is only ever reached because the lifter pressed the button. Finishing a cycle never
 * creates the next one.
 */
class StartNextTrainingCycleUseCase @Inject constructor(
    private val getCurrentReferenceMaxes: GetCurrentReferenceMaxSnapshotUseCase,
    private val calculateNextReferenceMaxes: CalculateNextCycleReferenceMaxUseCase,
    private val startTrainingCycle: StartTrainingCycleUseCase,
) {

    suspend operator fun invoke(decision: CycleProgressionDecision): Result<TrainingCycle> =
        runCatching {
            val current = getCurrentReferenceMaxes().getOrThrow()
            val progression = calculateNextReferenceMaxes(current, decision).getOrThrow()
            startTrainingCycle(progression.next).getOrThrow()
        }
}
