package com.griffgym.application.workout

import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.repository.ExerciseRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import javax.inject.Inject

/** Adds an unplanned movement to a running session. */
class AddExerciseToWorkoutUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    suspend operator fun invoke(
        sessionId: Long,
        exerciseId: Long,
        type: ExerciseType = ExerciseType.ACCESSORY,
    ): Result<Long> {
        val session = sessionRepository.getSession(sessionId)
            ?: return Result.failure(IllegalArgumentException("Unknown session $sessionId"))
        if (session.isReadOnly) {
            return Result.failure(IllegalStateException("Session $sessionId is read-only"))
        }
        exerciseRepository.getExercise(exerciseId)
            ?: return Result.failure(IllegalArgumentException("Unknown exercise $exerciseId"))

        return Result.success(sessionRepository.addExercise(sessionId, exerciseId, type))
    }
}

/** Appends an extra set, pre-filled with nothing — the plan did not ask for it. */
class AddSetUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
) {
    suspend operator fun invoke(exerciseLogId: Long): Result<Long> =
        runCatching { sessionRepository.addSet(exerciseLogId) }
}

class RemoveSetUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
) {
    suspend operator fun invoke(setLogId: Long): Result<Unit> =
        runCatching { sessionRepository.removeSet(setLogId) }
}

class UpdateWorkoutNotesUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
) {
    suspend operator fun invoke(sessionId: Long, notes: String?): Result<Unit> =
        runCatching { sessionRepository.updateSessionNotes(sessionId, notes?.trim()?.takeIf { it.isNotEmpty() }) }
}
