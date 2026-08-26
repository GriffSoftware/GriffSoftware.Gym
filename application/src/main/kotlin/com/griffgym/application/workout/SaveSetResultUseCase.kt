package com.griffgym.application.workout

import com.griffgym.domain.repository.WorkoutSessionRepository
import javax.inject.Inject

/**
 * Marks a set as done. A completed set must carry both a load and a rep count —
 * anything else would poison volume and 1RM statistics.
 */
class SaveSetResultUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val validate: ValidateSetInputUseCase,
) {
    suspend operator fun invoke(setLogId: Long, input: SetInput): SetValidation {
        val validation = validate(input, requireComplete = true)
        if (validation is SetValidation.Valid) {
            sessionRepository.updateSet(setLogId, validation.result)
        }
        return validation
    }
}
