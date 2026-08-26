package com.griffgym.domain.model

/**
 * A one rep max estimated from a submaximal set.
 *
 * The estimate is only ever as good as its input, so the set it was derived from
 * travels with the value.
 */
data class EstimatedOneRepMax(
    val weight: Weight,
    val sourceWeight: Weight,
    val sourceReps: Int,
) {
    /** Epley loses accuracy quickly past ten reps; the UI warns about exactly this. */
    val isReliable: Boolean get() = sourceReps in 1..10
}

/**
 * Epley: `1RM = weight * (1 + reps / 30)`.
 *
 * A single rep is already a true one rep max, so it is returned untouched rather than
 * inflated by the formula.
 */
object OneRepMaxCalculator {

    const val FORMULA_NAME: String = "Epley"

    fun estimate(weight: Weight, reps: Int): EstimatedOneRepMax? {
        if (reps < 1 || weight.isZero) return null
        val estimated = if (reps == 1) weight else weight * (1.0 + reps / 30.0)
        return EstimatedOneRepMax(weight = estimated, sourceWeight = weight, sourceReps = reps)
    }
}
