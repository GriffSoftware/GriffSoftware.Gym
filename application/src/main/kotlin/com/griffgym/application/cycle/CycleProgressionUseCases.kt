package com.griffgym.application.cycle

import com.griffgym.domain.model.CycleProgression
import com.griffgym.domain.model.CycleProgressionDecision
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.Weight
import com.griffgym.domain.repository.ReferenceMaxRepository
import com.griffgym.domain.repository.TrainingCycleRepository
import javax.inject.Inject

/**
 * The maxes the next cycle should be calculated from, before any decision is applied.
 *
 * The live `reference_max` table wins over the cycle's own snapshot. Both are real records,
 * but they answer different questions: the snapshot says what *this* block was built from and
 * never changes, while the table says what the lifter believes their maxes are today. A max
 * corrected on Home halfway through a block is the number they meant, and progressing from
 * the frozen snapshot would silently throw that correction away.
 *
 * The snapshot is still the fallback, so a cycle whose live rows went missing can be
 * progressed rather than dead-ending.
 */
class GetCurrentReferenceMaxSnapshotUseCase @Inject constructor(
    private val referenceMaxRepository: ReferenceMaxRepository,
    private val cycleRepository: TrainingCycleRepository,
) {
    suspend operator fun invoke(): Result<ReferenceMaxSnapshot> = runCatching {
        val live = referenceMaxRepository.observeReferenceMaxesOnce()
        val snapshot = cycleRepository.getCurrentCycle()?.referenceMaxes

        val resolved = ExerciseCategory.bigThree.associateWith { category ->
            val weight = live[category]?.takeIf { !it.isZero }
                ?: snapshot?.get(category)?.takeIf { !it.isZero }
            requireNotNull(weight) { "No reference max on file for $category" }
        }
        ReferenceMaxSnapshot.of(resolved)
    }

    private suspend fun ReferenceMaxRepository.observeReferenceMaxesOnce(): Map<ExerciseCategory, Weight> =
        ExerciseCategory.bigThree
            .mapNotNull { category -> getReferenceMax(category)?.let { category to it.weight } }
            .toMap()
}

/**
 * Applies the lifter's three decisions to their current maxes.
 *
 * Pure arithmetic — the rules it enforces (a change must be finite, and no lift may end up
 * at zero or below) live on [CycleProgression] and its parts. This use case exists to name
 * the business action and to hand callers a [Result] rather than an exception, the same split
 * `CalculateEstimated1RmUseCase` uses for the Epley formula.
 */
class CalculateNextCycleReferenceMaxUseCase @Inject constructor() {

    operator fun invoke(
        current: ReferenceMaxSnapshot,
        decision: CycleProgressionDecision,
    ): Result<CycleProgression> = runCatching { CycleProgression.from(current, decision) }
}
