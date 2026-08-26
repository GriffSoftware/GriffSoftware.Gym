package com.griffgym.application.workout

import com.griffgym.domain.repository.WorkoutSessionRepository
import javax.inject.Inject

/**
 * Persists a set while it is still being edited.
 *
 * Every keystroke that parses is written straight through to Room, which is what makes
 * a half-logged workout survive the app being killed mid-session. Partial input is fine
 * here; only [SaveSetResultUseCase] insists on a complete set.
 */
class UpdateSetResultUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val validate: ValidateSetInputUseCase,
) {
    suspend operator fun invoke(
        setLogId: Long,
        input: SetInput,
        completed: Boolean,
    ): SetValidation {
        val validation = validate(input, requireComplete = false)
        if (validation is SetValidation.Valid) {
            sessionRepository.updateSet(setLogId, validation.result.copy(completed = completed))
        }
        return validation
    }
}
