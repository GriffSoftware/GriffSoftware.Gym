package com.griffgym.application.metrics

import com.griffgym.domain.model.EstimatedOneRepMax
import com.griffgym.domain.model.OneRepMaxCalculator
import com.griffgym.domain.model.Weight
import javax.inject.Inject

/** Epley one rep max estimate, `weight * (1 + reps / 30)`. */
class CalculateEstimated1RmUseCase @Inject constructor() {

    operator fun invoke(weight: Weight, reps: Int): EstimatedOneRepMax? =
        OneRepMaxCalculator.estimate(weight, reps)

    /** Convenience overload for the calculator screen, which works with raw text. */
    operator fun invoke(weightInput: String, reps: Int): EstimatedOneRepMax? {
        val weight = Weight.parse(weightInput) ?: return null
        return invoke(weight, reps)
    }
}
