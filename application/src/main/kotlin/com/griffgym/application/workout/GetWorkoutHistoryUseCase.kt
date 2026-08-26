package com.griffgym.application.workout

import com.griffgym.domain.model.WorkoutSession
import com.griffgym.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Finished sessions, newest first. */
class GetWorkoutHistoryUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
) {
    operator fun invoke(): Flow<List<WorkoutSession>> = sessionRepository.observeHistory()
}
