package com.griffgym.application.workout

import com.griffgym.domain.repository.WorkoutSessionRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Abandons a session. The program pointer is left untouched, so the same unit is
 * offered again next time.
 */
class CancelWorkoutUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(sessionId: Long): Result<Unit> {
        val session = sessionRepository.getSession(sessionId)
            ?: return Result.failure(IllegalArgumentException("Unknown session $sessionId"))
        if (session.status.isFinished) {
            return Result.failure(IllegalStateException("Session $sessionId is already finished"))
        }
        sessionRepository.cancelSession(sessionId, clock.instant())
        return Result.success(Unit)
    }
}
