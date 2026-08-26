package com.griffgym.application.cycle

import com.griffgym.domain.model.CycleComparison
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.TrainingCycleSummary
import com.griffgym.domain.model.TrainingProgram
import com.griffgym.domain.repository.TrainingCycleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The cycle the lifter is in or has just finished.
 *
 * Home leans on this for the "CYCLE N" context line above the week and day: which block a
 * session belongs to is not something a lifter should have to leave the screen to find out.
 */
class GetCurrentCycleUseCase @Inject constructor(
    private val cycleRepository: TrainingCycleRepository,
) {
    operator fun invoke(): Flow<TrainingCycle?> = cycleRepository.observeCurrentCycle()
}

/** Everything the cycles screen shows, as one value that cannot be internally inconsistent. */
data class CycleOverview(
    val current: TrainingCycleSummary?,
    /** Newest first, current cycle excluded — the "previous cycles" list. */
    val previous: List<TrainingCycleSummary>,
) {
    /** The "vs previous cycle" section, present only once there is something to compare to. */
    val comparison: CycleComparison?
        get() {
            val currentCycle = current?.cycle ?: return null
            val previousCycle = previous
                .map { it.cycle }
                .firstOrNull { it.cycleNumber == currentCycle.cycleNumber - 1 }
                ?: return null
            return CycleComparison(previous = previousCycle, current = currentCycle)
        }
}

/**
 * Splits the cycle history into "the one you are in" and "the ones behind you".
 *
 * Both come off a single ordered flow, so the screen can never show a cycle in the history
 * list and in the active card at the same time.
 */
class GetCycleOverviewUseCase @Inject constructor(
    private val cycleRepository: TrainingCycleRepository,
) {
    operator fun invoke(): Flow<CycleOverview> =
        cycleRepository.observeCycleSummaries().map { summaries ->
            CycleOverview(
                current = summaries.firstOrNull(),
                previous = summaries.drop(1),
            )
        }
}

/** A finished cycle exactly as it was trained. Read-only: nothing here can be edited. */
data class CycleDetail(
    val summary: TrainingCycleSummary,
    val program: TrainingProgram?,
)

class GetCycleDetailUseCase @Inject constructor(
    private val cycleRepository: TrainingCycleRepository,
) {
    suspend operator fun invoke(cycleId: Long): CycleDetail? {
        val summary = cycleRepository.getCycleSummary(cycleId) ?: return null
        return CycleDetail(summary = summary, program = cycleRepository.getCycleProgram(cycleId))
    }
}

/** The closing report on a cycle, and the numbers the next one would start from. */
data class CycleReview(
    val summary: TrainingCycleSummary,
    val currentReferenceMaxes: ReferenceMaxSnapshot,
) {
    val nextCycleNumber: Int get() = summary.cycle.cycleNumber + 1
}

/**
 * What the review screen needs before the lifter decides anything.
 *
 * Fails rather than guessing when there is no cycle or no usable maxes: the screen has
 * nothing to review in that case, and inventing numbers to fill it in would be worse than
 * saying so.
 */
class GetCycleReviewUseCase @Inject constructor(
    private val cycleRepository: TrainingCycleRepository,
    private val getCurrentReferenceMaxes: GetCurrentReferenceMaxSnapshotUseCase,
) {
    suspend operator fun invoke(): Result<CycleReview> = runCatching {
        val cycle = requireNotNull(cycleRepository.getCurrentCycle()) { "There is no cycle to review" }
        val summary = requireNotNull(cycleRepository.getCycleSummary(cycle.id)) {
            "Cycle ${cycle.cycleNumber} has no plan to report on"
        }
        CycleReview(
            summary = summary,
            currentReferenceMaxes = getCurrentReferenceMaxes().getOrThrow(),
        )
    }
}
