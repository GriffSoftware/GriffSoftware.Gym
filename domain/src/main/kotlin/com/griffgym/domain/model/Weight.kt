package com.griffgym.domain.model

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A training load expressed in kilograms.
 *
 * Loads on this program move in 2.5 kg and 1.25 kg steps, which means half kilograms
 * (117.5, 132.5, 142.5, 162.5 ...) are first class citizens — a weight is therefore never
 * an [Int]. Values are normalised to two decimals so that arithmetic never leaks
 * floating point noise into the database or the UI.
 */
@JvmInline
value class Weight private constructor(val kilograms: Double) : Comparable<Weight> {

    val isZero: Boolean get() = kilograms == 0.0

    operator fun plus(other: Weight): Weight = of(kilograms + other.kilograms)

    operator fun minus(other: Weight): Weight = of(kilograms - other.kilograms)

    operator fun times(factor: Double): Weight = of(kilograms * factor)

    fun percentage(percent: Int): Weight = of(kilograms * percent / 100.0)

    override fun compareTo(other: Weight): Int = kilograms.compareTo(other.kilograms)

    /** `192.5` -> "192.5", `150.0` -> "150". */
    override fun toString(): String = format()

    fun format(): String {
        val rounded = round(kilograms)
        return if (abs(rounded % 1.0) < EPSILON) {
            rounded.toLong().toString()
        } else {
            trimTrailingZero(rounded)
        }
    }

    companion object {
        private const val EPSILON = 1e-6

        val ZERO: Weight = Weight(0.0)

        fun of(kilograms: Double): Weight {
            require(kilograms.isFinite()) { "Weight must be a finite number, was $kilograms" }
            require(kilograms >= 0.0) { "Weight cannot be negative, was $kilograms" }
            return Weight(round(kilograms))
        }

        fun of(kilograms: Int): Weight = of(kilograms.toDouble())

        /**
         * Parses user input. Accepts both the dot and the comma as a decimal separator
         * because Polish keyboards produce ",".
         */
        fun parse(raw: String): Weight? {
            val normalised = raw.trim().replace(',', '.').replace(" ", "")
            if (normalised.isEmpty()) return null
            val value = normalised.toDoubleOrNull() ?: return null
            if (!value.isFinite() || value < 0.0) return null
            return of(value)
        }

        private fun round(value: Double): Double = (value * 100).roundToLong() / 100.0

        private fun trimTrailingZero(value: Double): String {
            val text = value.toString()
            return if (text.endsWith("0") && text.contains('.') && text.length > 3) {
                text.trimEnd('0').trimEnd('.')
            } else {
                text
            }
        }
    }
}
