package com.griffgym.domain.model

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Rate of Perceived Exertion — subjective intensity of a single set, 1.0 .. 10.0 in 0.5 steps.
 */
@JvmInline
value class Rpe private constructor(val value: Double) : Comparable<Rpe> {

    override fun compareTo(other: Rpe): Int = value.compareTo(other.value)

    override fun toString(): String = format()

    fun format(): String =
        if (abs(value % 1.0) < EPSILON) value.toLong().toString() else value.toString()

    fun stepUp(): Rpe = ofOrNull(value + STEP) ?: this

    fun stepDown(): Rpe = ofOrNull(value - STEP) ?: this

    companion object {
        private const val EPSILON = 1e-6

        const val MIN_VALUE: Double = 1.0
        const val MAX_VALUE: Double = 10.0
        const val STEP: Double = 0.5

        val MIN: Rpe = Rpe(MIN_VALUE)
        val MAX: Rpe = Rpe(MAX_VALUE)

        fun of(value: Double): Rpe = requireNotNull(ofOrNull(value)) {
            "RPE must be within $MIN_VALUE..$MAX_VALUE in steps of $STEP, was $value"
        }

        /** Returns `null` instead of throwing — used for user input validation. */
        fun ofOrNull(value: Double): Rpe? {
            if (!value.isFinite()) return null
            val snapped = snap(value)
            if (snapped < MIN_VALUE || snapped > MAX_VALUE) return null
            return Rpe(snapped)
        }

        fun parse(raw: String): Rpe? {
            val normalised = raw.trim().replace(',', '.')
            if (normalised.isEmpty()) return null
            return normalised.toDoubleOrNull()?.let(::ofOrNull)
        }

        fun isValid(value: Double): Boolean = ofOrNull(value) != null

        /** Snaps to the nearest 0.5 so the UI can never persist an RPE of 7.31. */
        private fun snap(value: Double): Double = (value / STEP).roundToLong() * STEP
    }
}

/**
 * A planned intensity, either an exact value ("RPE 8") or a range ("RPE 6-7") as used
 * for accessory work.
 */
data class RpeTarget(val min: Rpe, val max: Rpe) {

    init {
        require(min <= max) { "RPE target range is inverted: $min > $max" }
    }

    val isRange: Boolean get() = min != max

    fun format(): String = if (isRange) "${min.format()}-${max.format()}" else min.format()

    companion object {
        fun exact(value: Double): RpeTarget = Rpe.of(value).let { RpeTarget(it, it) }

        fun range(min: Double, max: Double): RpeTarget = RpeTarget(Rpe.of(min), Rpe.of(max))
    }
}
