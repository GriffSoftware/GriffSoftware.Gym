package com.griffgym.application.workout

import com.griffgym.domain.model.CurrentWorkout
import com.griffgym.domain.repository.TrainingCycleRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * What the lifter should be doing right now: resume a running session, start the next
 * planned unit, review the cycle they have just finished, or nothing at all because there is
 * no plan.
 *
 * "The program has run out of units" and "there is a cycle here" is treated as a finished
 * cycle regardless of the stored status flag. The two normally agree — [CompleteWorkoutUseCase]
 * sets both at once — but an installation upgraded from before cycles existed can hold a
 * program that was already finished, and sending that lifter to a dead end rather than to
 * their review screen would be the wrong call.
 */
class GetCurrentWorkoutUseCase @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val programRepository: TrainingProgramRepository,
    private val cycleRepository: TrainingCycleRepository,
) {
    operator fun invoke(): Flow<CurrentWorkout> = combine(
        sessionRepository.observeActiveSession(),
        programRepository.observeCurrentWorkoutTemplate(),
        cycleRepository.observeCurrentCycle(),
    ) { activeSession, template, cycle ->
        when {
            activeSession != null -> CurrentWorkout.Active(activeSession)
            template != null -> CurrentWorkout.Planned(template)
            cycle != null -> CurrentWorkout.CycleCompleted(cycle)
            else -> CurrentWorkout.NoProgram
        }
    }
}
