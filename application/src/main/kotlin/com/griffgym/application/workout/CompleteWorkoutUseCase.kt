package com.griffgym.application.workout

import com.griffgym.application.metrics.CalculateWorkoutVolumeUseCase
import com.griffgym.domain.repository.TrainingCycleRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Closes a session and moves the program forward.
 *
 * The pointer only advances when the finished session really is the current unit, so
 * replaying an old workout can never skip the plan ahead.
 *
 * When there is no unit after the one just finished, the cycle is over. That is the only
 * thing that ever completes a cycle: not six calendar weeks passing, not a date on a
 * planner — the last scheduled workout actually being logged. Closing it also clears the
 * program pointer, and the repository does both in one transaction because they are one
 * fact. What it does *not* do is create the next cycle: the app now waits, visibly, for the
 * lifter to decide.
 */
class CompleteWorkoutUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val programRepository: TrainingProgramRepository,
    private val cycleRepository: TrainingCycleRepository,
    private val calculateVolume: CalculateWorkoutVolumeUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(sessionId: Long): Result<Unit> {
        val session = sessionRepository.getSession(sessionId)
            ?: return Result.failure(IllegalArgumentException("Unknown session $sessionId"))

        if (session.status.isFinished) {
            return Result.failure(IllegalStateException("Session $sessionId is already finished"))
        }

        val finishedAt = clock.instant()

        sessionRepository.completeSession(
            sessionId = sessionId,
            finishedAt = finishedAt,
            totalVolume = calculateVolume(session),
        )

        val templateId = session.templateId
        if (templateId != null && programRepository.getCurrentWorkoutTemplate()?.id == templateId) {
            val template = programRepository.getWorkoutTemplate(templateId)
            val next = template?.let { programRepository.getWorkoutTemplateAfter(it.sequenceNumber) }
            if (next != null) {
                programRepository.setCurrentWorkoutTemplate(next.id)
            } else {
                cycleRepository.completeCurrentCycle(finishedAt)
            }
        }

        return Result.success(Unit)
    }
}
