package com.griffgym.domain.model

import java.time.LocalDate

/**
 * The lifter's declared current maximum for one of the big three.
 *
 * It is a reference point for planning — deliberately *not* a personal record, because
 * it was never performed inside the app.
 */
data class ReferenceMax(
    val category: ExerciseCategory,
    val weight: Weight,
    val updatedOn: LocalDate,
) {
    init {
        require(category.isBigThree) { "Reference maxes only exist for the big three, got $category" }
    }
}

/**
 * One achievement backing a personal record, always traceable to the session it happened in.
 *
 * [weight] is the one rep max — measured for a true single, estimated otherwise — while
 * [liftedWeight] is what was actually on the bar, so the UI can show where an estimate
 * came from.
 */
data class OneRepMaxAchievement(
    val weight: Weight,
    val liftedWeight: Weight,
    val reps: Int,
    val achievedOn: LocalDate,
    val sessionId: Long,
) {
    val isEstimate: Boolean get() = reps > 1
}

/**
 * Best results actually logged in the app for a lift.
 *
 * [bestActual] is a genuine single; [bestEstimated] is the best Epley estimate across all
 * logged sets. Both may be absent before the first workout is completed.
 */
data class PersonalRecord(
    val category: ExerciseCategory,
    val bestActual: OneRepMaxAchievement?,
    val bestEstimated: OneRepMaxAchievement?,
) {
    val hasAnyRecord: Boolean get() = bestActual != null || bestEstimated != null
}

/** One point on the "Big 3 — 1RM progression" chart. */
data class OneRepMaxPoint(
    val date: LocalDate,
    val sessionId: Long,
    val estimated: Weight,
)

/** One day the lifter trained, used by the consistency calendar. */
data class TrainingDaySummary(
    val date: LocalDate,
    val sessionId: Long,
    val volume: TrainingVolume,
    val hasPersonalRecord: Boolean,
)
