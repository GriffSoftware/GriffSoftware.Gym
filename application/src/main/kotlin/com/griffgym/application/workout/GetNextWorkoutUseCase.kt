package com.griffgym.application.workout

import com.griffgym.domain.model.WorkoutTemplate
import com.griffgym.domain.repository.TrainingProgramRepository
import javax.inject.Inject

/**
 * The unit that follows the current one in program order.
 *
 * The program is a sequence, not a calendar: Week 1 Day III is followed by Week 2 Day I
 * whether that happens tomorrow or in ten days.
 */
class GetNextWorkoutUseCase @Inject constructor(
    private val programRepository: TrainingProgramRepository,
) {
    suspend operator fun invoke(): WorkoutTemplate? {
        val current = programRepository.getCurrentWorkoutTemplate() ?: return null
        return programRepository.getWorkoutTemplateAfter(current.sequenceNumber)
    }

    suspend fun after(template: WorkoutTemplate): WorkoutTemplate? =
        programRepository.getWorkoutTemplateAfter(template.sequenceNumber)
}
