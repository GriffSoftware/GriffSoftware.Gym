package com.griffgym.application.workout

import com.griffgym.domain.model.CurrentWorkout
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * What the lifter should be doing right now: resume a running session, start the next
 * planned unit, or nothing because the program is finished.
 */
class GetCurrentWorkoutUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val programRepository: TrainingProgramRepository,
) {
    operator fun invoke(): Flow<CurrentWorkout> = combine(
        sessionRepository.observeActiveSession(),
        programRepository.observeCurrentWorkoutTemplate(),
    ) { activeSession, template ->
        when {
            activeSession != null -> CurrentWorkout.Active(activeSession)
            template != null -> CurrentWorkout.Planned(template)
            else -> CurrentWorkout.ProgramCompleted
        }
    }
}
