package com.griffgym.application.exercise

import com.griffgym.domain.model.Exercise
import com.griffgym.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The exercise catalogue, used by the "add exercise" picker. */
class GetExercisesUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
) {
    operator fun invoke(): Flow<List<Exercise>> = exerciseRepository.observeExercises()
}
