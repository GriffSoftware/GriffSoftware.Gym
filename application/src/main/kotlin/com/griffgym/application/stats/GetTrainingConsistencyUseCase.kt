package com.griffgym.application.stats

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.TrainingDaySummary
import com.griffgym.domain.model.Weight
import com.griffgym.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Every day that was trained, for the contribution-style calendar.
 *
 * A day is flagged as a record day when it pushed the running best estimate for one of
 * the big three past everything that came before it.
 */
class GetTrainingConsistencyUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
) {
    operator fun invoke(): Flow<List<TrainingDaySummary>> =
        sessionRepository.observeCompletedSessions().map { sessions ->
            val runningBest = mutableMapOf<ExerciseCategory, Weight>()
            sessions.sortedBy { it.date }.map { session ->
                var improved = false
                ExerciseCategory.bigThree.forEach { category ->
                    val best = SessionAnalysis.bestEstimate(session, category)?.weight
                    if (best != null && best > (runningBest[category] ?: Weight.ZERO)) {
                        runningBest[category] = best
                        improved = true
                    }
                }
                TrainingDaySummary(
                    date = session.date,
                    sessionId = session.id,
                    volume = session.totalVolume,
                    hasPersonalRecord = improved,
                )
            }
        }
}
