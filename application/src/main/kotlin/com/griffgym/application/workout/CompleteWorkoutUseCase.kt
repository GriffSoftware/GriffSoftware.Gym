package com.griffgym.application.workout

import com.griffgym.application.metrics.CalculateWorkoutVolumeUseCase
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Closes a session and moves the program forward.
 *
 * The pointer only advances when the finished session really is the current unit, so
 * replaying an old workout can never skip the plan ahead.
 */
class CompleteWorkoutUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val programRepository: TrainingProgramRepository,
    private val calculateVolume: CalculateWorkoutVolumeUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(sessionId: Long): Result<Unit> {
        val session = sessionRepository.getSession(sessionId)
            ?: return Result.failure(IllegalArgumentException("Unknown session $sessionId"))

        if (session.status.isFinished) {
            return Result.failure(IllegalStateException("Session $sessionId is already finished"))
        }

        sessionRepository.completeSession(
            sessionId = sessionId,
            finishedAt = clock.instant(),
            totalVolume = calculateVolume(session),
        )

        val templateId = session.templateId
        if (templateId != null && programRepository.getCurrentWorkoutTemplate()?.id == templateId) {
            val template = programRepository.getWorkoutTemplate(templateId)
            val next = template?.let { programRepository.getWorkoutTemplateAfter(it.sequenceNumber) }
            programRepository.setCurrentWorkoutTemplate(next?.id)
        }

        return Result.success(Unit)
    }
}
