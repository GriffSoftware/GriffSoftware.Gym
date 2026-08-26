package com.griffgym.application.workout

import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Snapshots the current template into a persisted session.
 *
 * Starting twice is not an error — an already running session is simply returned, which
 * makes the START/CONTINUE button on Home idempotent.
 */
class StartWorkoutUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val programRepository: TrainingProgramRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Result<Long> {
        sessionRepository.getActiveSession()?.let { return Result.success(it.id) }

        val template = programRepository.getCurrentWorkoutTemplate()
            ?: return Result.failure(IllegalStateException("There is no workout left to start"))

        val now = clock.instant()
        val id = sessionRepository.startSession(
            template = template,
            date = LocalDate.now(clock),
            startedAt = now,
        )
        return Result.success(id)
    }
}
