package com.griffgym.application.stats

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.OneRepMaxAchievement
import com.griffgym.domain.model.PersonalRecord
import com.griffgym.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Bests that were genuinely lifted inside the app.
 *
 * A reference max is a planning number the lifter typed in, so it is deliberately not a
 * record here — only completed sets can produce one.
 */
class GetPersonalRecordsUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
) {
    operator fun invoke(): Flow<List<PersonalRecord>> =
        sessionRepository.observeCompletedSessions().map { sessions ->
            ExerciseCategory.bigThree.map { category ->
                var bestActual: OneRepMaxAchievement? = null
                var bestEstimated: OneRepMaxAchievement? = null
                sessions.forEach { session ->
                    bestActual = SessionAnalysis.better(
                        bestActual,
                        SessionAnalysis.bestActualSingle(session, category),
                    )
                    bestEstimated = SessionAnalysis.better(
                        bestEstimated,
                        SessionAnalysis.bestEstimate(session, category),
                    )
                }
                PersonalRecord(
                    category = category,
                    bestActual = bestActual,
                    bestEstimated = bestEstimated,
                )
            }
        }
}
