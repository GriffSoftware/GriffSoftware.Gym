package com.griffgym.application.workout

import com.griffgym.domain.model.WorkoutSession
import com.griffgym.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWorkoutSessionUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
) {
    operator fun invoke(sessionId: Long): Flow<WorkoutSession?> =
        sessionRepository.observeSession(sessionId)
}
