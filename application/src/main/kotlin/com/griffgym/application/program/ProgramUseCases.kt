package com.griffgym.application.program

import com.griffgym.domain.model.TrainingProgram
import com.griffgym.domain.model.TrainingWeek
import com.griffgym.domain.repository.TrainingProgramRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTrainingProgramUseCase @Inject constructor(
    private val programRepository: TrainingProgramRepository,
) {
    operator fun invoke(): Flow<TrainingProgram?> = programRepository.observeActiveProgram()
}

class GetCurrentTrainingWeekUseCase @Inject constructor(
    private val programRepository: TrainingProgramRepository,
) {
    operator fun invoke(): Flow<TrainingWeek?> = programRepository.observeCurrentTrainingWeek()
}
