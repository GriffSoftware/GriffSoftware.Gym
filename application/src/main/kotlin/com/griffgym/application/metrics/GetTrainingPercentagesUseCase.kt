package com.griffgym.application.metrics

import com.griffgym.domain.model.Weight
import javax.inject.Inject

/**
 * A percentage of a one rep max together with the rep range that intensity supports.
 */
data class TrainingPercentage(
    val percent: Int,
    val weight: Weight,
    val minReps: Int,
    val maxReps: Int?,
) {
    fun formatReps(): String = when {
        maxReps == null -> "$minReps+"
        maxReps == minReps -> "$minReps"
        else -> "$minReps - $maxReps"
    }
}

/**
 * The classic intensity/repetition table. The rep ranges are training doctrine, which is
 * exactly why they live in a use case instead of being hard-coded into a Composable.
 */
class GetTrainingPercentagesUseCase @Inject constructor() {

    operator fun invoke(oneRepMax: Weight): List<TrainingPercentage> = RANGES.map { (percent, reps) ->
        TrainingPercentage(
            percent = percent,
            weight = oneRepMax.percentage(percent),
            minReps = reps.first,
            maxReps = reps.second,
        )
    }

    private companion object {
        val RANGES: List<Pair<Int, Pair<Int, Int?>>> = listOf(
            100 to (1 to 1),
            95 to (2 to 2),
            90 to (3 to 4),
            85 to (5 to 6),
            80 to (7 to 8),
            75 to (9 to 10),
            70 to (10 to 12),
            65 to (12 to 15),
            60 to (15 to null),
        )
    }
}
