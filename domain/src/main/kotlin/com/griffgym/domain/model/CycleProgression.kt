package com.griffgym.domain.model

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A signed change to a reference max, in kilograms.
 *
 * Deliberately not a [Weight]: a weight is something on a bar and can never be negative,
 * while lowering a reference max after illness, a bad cycle or a stretch away from the gym
 * is a normal, supported decision. Values are normalised to two decimals for the same reason
 * [Weight] is — so arithmetic never leaks floating point noise into the database or the UI.
 */
@JvmInline
value class ReferenceMaxDelta private constructor(val kilograms: Double) : Comparable<ReferenceMaxDelta> {

    val isZero: Boolean get() = kilograms == 0.0

    val isIncrease: Boolean get() = kilograms > 0.0

    val isDecrease: Boolean get() = kilograms < 0.0

    override fun compareTo(other: ReferenceMaxDelta): Int = kilograms.compareTo(other.kilograms)

    override fun toString(): String = format()

    /** "+5", "-2.5", "0" — always signed, because the sign is the whole point. */
    fun format(): String {
        val magnitude = Weight.of(abs(kilograms)).format()
        return when {
            kilograms > 0.0 -> "+$magnitude"
            kilograms < 0.0 -> "-$magnitude"
            else -> "0"
        }
    }

    companion object {
        val NONE: ReferenceMaxDelta = ReferenceMaxDelta(0.0)

        fun of(kilograms: Double): ReferenceMaxDelta {
            require(kilograms.isFinite()) { "A reference max change must be finite, was $kilograms" }
            return ReferenceMaxDelta((kilograms * 100).roundToLong() / 100.0)
        }

        /**
         * Parses user input. Accepts a leading `+` or `-`, and both the dot and the comma as
         * a decimal separator, matching [Weight.parse].
         */
        fun parse(raw: String): ReferenceMaxDelta? {
            val normalised = raw.trim().replace(',', '.').replace(" ", "").removePrefix("+")
            if (normalised.isEmpty() || normalised == "-") return null
            val value = normalised.toDoubleOrNull() ?: return null
            if (!value.isFinite()) return null
            return of(value)
        }
    }
}

/**
 * What the lifter decided to do with one reference max before the next cycle.
 *
 * The three cases are kept apart even though two of them carry the same number, because the
 * UI has to remember which button is selected and "keep" is a decision, not the absence of
 * one.
 */
sealed interface ReferenceMaxChange {

    val delta: ReferenceMaxDelta

    /** Same max as this cycle: a block repeated at the same intensity. */
    data object Keep : ReferenceMaxChange {
        override val delta: ReferenceMaxDelta get() = ReferenceMaxDelta.NONE
    }

    /** The suggested step up, from [DefaultCycleProgressionPolicy]. */
    data class Increase(override val delta: ReferenceMaxDelta) : ReferenceMaxChange {
        init {
            require(delta.isIncrease) { "An increase must move the max up, was $delta" }
        }
    }

    /** Anything the lifter typed themselves, up or down. */
    data class Custom(override val delta: ReferenceMaxDelta) : ReferenceMaxChange
}

/**
 * The default step up per lift, in kilograms.
 *
 * Lives here rather than in a ViewModel or a Composable because "how much a lifter adds to
 * a max between blocks" is a training rule, not a piece of screen state. The bench moves in
 * half steps because upper body strength does.
 */
object DefaultCycleProgressionPolicy {

    private val INCREASE_KG: Map<ExerciseCategory, Double> = mapOf(
        ExerciseCategory.SQUAT to 5.0,
        ExerciseCategory.BENCH_PRESS to 2.5,
        ExerciseCategory.DEADLIFT to 5.0,
    )

    fun defaultIncrease(category: ExerciseCategory): ReferenceMaxDelta {
        val kilograms = requireNotNull(INCREASE_KG[category]) {
            "Only the big three progress between cycles, got $category"
        }
        return ReferenceMaxDelta.of(kilograms)
    }

    fun defaultChange(category: ExerciseCategory): ReferenceMaxChange =
        ReferenceMaxChange.Increase(defaultIncrease(category))

    /** What the review screen opens with: every lift stepped up by its own default. */
    fun defaultDecision(): CycleProgressionDecision = CycleProgressionDecision(
        squat = defaultChange(ExerciseCategory.SQUAT),
        benchPress = defaultChange(ExerciseCategory.BENCH_PRESS),
        deadlift = defaultChange(ExerciseCategory.DEADLIFT),
    )
}

/**
 * The three per-lift decisions behind the next cycle.
 *
 * Each lift is decided on its own: a lifter who added 5 kg to their squat, held their bench
 * and dropped their deadlift after a tweak has made three perfectly ordinary decisions.
 */
data class CycleProgressionDecision(
    val squat: ReferenceMaxChange,
    val benchPress: ReferenceMaxChange,
    val deadlift: ReferenceMaxChange,
) {
    operator fun get(category: ExerciseCategory): ReferenceMaxChange? = when (category) {
        ExerciseCategory.SQUAT -> squat
        ExerciseCategory.BENCH_PRESS -> benchPress
        ExerciseCategory.DEADLIFT -> deadlift
        ExerciseCategory.ACCESSORY -> null
    }

    fun with(category: ExerciseCategory, change: ReferenceMaxChange): CycleProgressionDecision =
        when (category) {
            ExerciseCategory.SQUAT -> copy(squat = change)
            ExerciseCategory.BENCH_PRESS -> copy(benchPress = change)
            ExerciseCategory.DEADLIFT -> copy(deadlift = change)
            ExerciseCategory.ACCESSORY -> this
        }
}

/** One lift's move from this cycle to the next: where it was, what changed, where it lands. */
data class LiftProgression(
    val category: ExerciseCategory,
    val current: Weight,
    val change: ReferenceMaxChange,
) {
    val next: Weight

    init {
        require(category.isBigThree) { "Only the big three carry a reference max, got $category" }
        val result = current.kilograms + change.delta.kilograms
        // A max of zero or less is not a lighter block, it is a block that cannot be built.
        require(result > 0.0) {
            "$category would end up at $result kg, which is not a max anyone can train from"
        }
        next = Weight.of(result)
    }
}

/**
 * The whole decision applied: three lifts, and the snapshot the next cycle will be built on.
 *
 * Pure and id-less on purpose — this is the calculation, not the act of starting a cycle.
 */
data class CycleProgression(
    val squat: LiftProgression,
    val benchPress: LiftProgression,
    val deadlift: LiftProgression,
) {
    /** Day I, II and III order, the same order the lifter meets the lifts in. */
    val lifts: List<LiftProgression> get() = listOf(squat, benchPress, deadlift)

    val current: ReferenceMaxSnapshot
        get() = ReferenceMaxSnapshot(squat.current, benchPress.current, deadlift.current)

    val next: ReferenceMaxSnapshot
        get() = ReferenceMaxSnapshot(squat.next, benchPress.next, deadlift.next)

    companion object {
        fun from(
            current: ReferenceMaxSnapshot,
            decision: CycleProgressionDecision,
        ): CycleProgression = CycleProgression(
            squat = LiftProgression(ExerciseCategory.SQUAT, current.squat, decision.squat),
            benchPress = LiftProgression(
                ExerciseCategory.BENCH_PRESS,
                current.benchPress,
                decision.benchPress,
            ),
            deadlift = LiftProgression(ExerciseCategory.DEADLIFT, current.deadlift, decision.deadlift),
        )
    }
}

/**
 * What changed between two cycles, read back from their snapshots after the fact.
 *
 * This is history rather than intent: it compares what two blocks were actually built from,
 * which is why it works for any pair of cycles and needs no record of the decision that
 * produced them.
 */
data class CycleComparison(
    val previous: TrainingCycle,
    val current: TrainingCycle,
) {
    data class LiftChange(
        val category: ExerciseCategory,
        val before: Weight,
        val after: Weight,
    ) {
        val delta: ReferenceMaxDelta = ReferenceMaxDelta.of(after.kilograms - before.kilograms)
    }

    val lifts: List<LiftChange> = ExerciseCategory.bigThree.map { category ->
        LiftChange(
            category = category,
            before = requireNotNull(previous.referenceMaxes[category]),
            after = requireNotNull(current.referenceMaxes[category]),
        )
    }
}
