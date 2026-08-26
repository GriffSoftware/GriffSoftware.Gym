package com.griffgym.application.metrics

import com.griffgym.domain.model.TrainingVolume
import com.griffgym.domain.model.WorkoutSession
import javax.inject.Inject

/**
 * Tonnage of a session: `weight x reps` summed over every *completed* set.
 *
 * Sets that were entered but never ticked off do not count — otherwise a workout that
 * was abandoned halfway would look like the hardest one of the block.
 */
class CalculateWorkoutVolumeUseCase @Inject constructor() {
    operator fun invoke(session: WorkoutSession): TrainingVolume = session.totalVolume
}
