package com.griffgym.infrastructure.repository

import com.griffgym.domain.repository.LocalTrainingDataRepository
import com.griffgym.domain.repository.ReferenceMaxRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Is there anything on this phone?" — the question that decides what happens after sign-in.
 *
 * Three cheap existence checks, ordered so the common cases short-circuit, and gathered behind
 * one contract because getting the answer wrong in either direction destroys data: a false
 * negative overwrites a lifter's history with an empty cloud, and a false positive stops a new
 * phone from restoring.
 */
@Singleton
class RoomLocalTrainingDataRepository @Inject constructor(
    private val referenceMaxRepository: ReferenceMaxRepository,
    private val trainingProgramRepository: TrainingProgramRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
) : LocalTrainingDataRepository {

    override suspend fun hasAnyTrainingData(): Boolean =
        referenceMaxRepository.hasAnyReferenceMax() ||
            trainingProgramRepository.hasProgram() ||
            workoutSessionRepository.hasAnySession()
}
