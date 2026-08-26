package com.griffgym.domain.model

import kotlin.math.roundToLong

/** Tonnage: the sum of `weight x reps` over a set, an exercise or a whole session. */
@JvmInline
value class TrainingVolume private constructor(val kilograms: Double) : Comparable<TrainingVolume> {

    val isZero: Boolean get() = kilograms == 0.0

    operator fun plus(other: TrainingVolume): TrainingVolume = of(kilograms + other.kilograms)

    override fun compareTo(other: TrainingVolume): Int = kilograms.compareTo(other.kilograms)

    override fun toString(): String = kilograms.roundToLong().toString()

    companion object {
        val ZERO: TrainingVolume = TrainingVolume(0.0)

        fun of(kilograms: Double): TrainingVolume {
            require(kilograms.isFinite() && kilograms >= 0.0) { "Invalid volume: $kilograms" }
            return TrainingVolume((kilograms * 100).roundToLong() / 100.0)
        }

        fun from(weight: Weight, reps: Int): TrainingVolume =
            if (reps <= 0) ZERO else of(weight.kilograms * reps)
    }
}

fun Iterable<TrainingVolume>.sum(): TrainingVolume =
    fold(TrainingVolume.ZERO) { acc, value -> acc + value }
