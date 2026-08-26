package com.griffgym.application.stats

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.OneRepMaxPoint
import com.griffgym.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * One point per session per lift for the "Big 3 — 1RM progression" chart, using the best
 * estimate that session produced.
 */
class GetOneRepMaxHistoryUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
) {
    operator fun invoke(): Flow<Map<ExerciseCategory, List<OneRepMaxPoint>>> =
        sessionRepository.observeCompletedSessions().map { sessions ->
            ExerciseCategory.bigThree.associateWith { category ->
                sessions
                    .mapNotNull { session ->
                        val best = SessionAnalysis.bestEstimate(session, category) ?: return@mapNotNull null
                        OneRepMaxPoint(
                            date = best.achievedOn,
                            sessionId = best.sessionId,
                            estimated = best.weight,
                        )
                    }
                    .sortedBy { it.date }
            }
        }
}
