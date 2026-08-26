package com.griffgym.domain.model

import kotlin.math.roundToLong

/**
 * Rounds a calculated load onto something that can actually be loaded on a bar.
 *
 * A generated program multiplies a reference max by a percentage, which produces values
 * like 161.428571 kg. Nobody loads that, so every planned weight is snapped to the plate
 * increment the gym works in — 2.5 kg, i.e. a 1.25 kg plate per side.
 *
 * Ties round up: 161.25 kg becomes 162.5 kg, which keeps the rule simple to reason about
 * and errs on the side of the prescribed intensity rather than under it.
 */
object WeightRoundingPolicy {

    /** The smallest jump the plan ever asks for: 1.25 kg on each side of the bar. */
    const val INCREMENT_KG: Double = 2.5

    fun round(kilograms: Double): Weight {
        require(kilograms.isFinite()) { "Cannot round a non-finite load, was $kilograms" }
        require(kilograms >= 0.0) { "Cannot round a negative load, was $kilograms" }
        return Weight.of((kilograms / INCREMENT_KG).roundToLong() * INCREMENT_KG)
    }

    fun round(weight: Weight): Weight = round(weight.kilograms)
}
