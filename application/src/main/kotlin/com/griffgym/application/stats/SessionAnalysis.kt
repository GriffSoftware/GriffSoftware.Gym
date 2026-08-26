package com.griffgym.application.stats

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.OneRepMaxAchievement
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutSession

/**
 * Shared reading of a finished session for the statistics use cases.
 *
 * Only main-lift work counts: an accessory triple on the Smith machine says nothing about
 * squat strength, so it never reaches the progression chart or the record list.
 */
internal object SessionAnalysis {

    fun bestEstimate(session: WorkoutSession, category: ExerciseCategory): OneRepMaxAchievement? =
        mainLiftSets(session, category)
            .mapNotNull { set ->
                val estimate = set.estimatedOneRepMax ?: return@mapNotNull null
                OneRepMaxAchievement(
                    weight = estimate.weight,
                    liftedWeight = estimate.sourceWeight,
                    reps = estimate.sourceReps,
                    achievedOn = session.date,
                    sessionId = session.id,
                )
            }
            .maxByOrNull { it.weight.kilograms }

    /** A genuine single: one rep, actually completed. */
    fun bestActualSingle(session: WorkoutSession, category: ExerciseCategory): OneRepMaxAchievement? =
        mainLiftSets(session, category)
            .filter { it.actualReps == 1 && it.completed }
            .mapNotNull { set ->
                val weight = set.actualWeight ?: return@mapNotNull null
                OneRepMaxAchievement(
                    weight = weight,
                    liftedWeight = weight,
                    reps = 1,
                    achievedOn = session.date,
                    sessionId = session.id,
                )
            }
            .maxByOrNull { it.weight.kilograms }

    private fun mainLiftSets(session: WorkoutSession, category: ExerciseCategory) =
        session.exercises
            .filter { it.exercise.category == category && it.type.isMainLift }
            .flatMap { it.sets }

    fun better(
        current: OneRepMaxAchievement?,
        candidate: OneRepMaxAchievement?,
    ): OneRepMaxAchievement? = when {
        candidate == null -> current
        current == null -> candidate
        candidate.weight > current.weight -> candidate
        else -> current
    }

    fun heavier(current: Weight?, candidate: Weight?): Weight? = when {
        candidate == null -> current
        current == null -> candidate
        else -> maxOf(current, candidate)
    }
}
