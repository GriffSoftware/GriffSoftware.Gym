package com.griffgym.infrastructure.repository

import com.griffgym.domain.model.TrainingProgram
import com.griffgym.domain.model.TrainingWeek
import com.griffgym.domain.model.WorkoutTemplate
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.infrastructure.database.dao.TrainingProgramDao
import com.griffgym.infrastructure.database.entity.ProgramProgressEntity
import com.griffgym.infrastructure.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTrainingProgramRepository @Inject constructor(
    private val programDao: TrainingProgramDao,
) : TrainingProgramRepository {

    override fun observeActiveProgram(): Flow<TrainingProgram?> =
        programDao.observeActiveProgram().map { it?.toDomain() }

    override suspend fun getActiveProgram(): TrainingProgram? =
        programDao.getActiveProgram()?.toDomain()

    override fun observeCurrentWorkoutTemplate(): Flow<WorkoutTemplate?> =
        programDao.observeCurrentTemplate().map { it?.toDomain() }

    override suspend fun getCurrentWorkoutTemplate(): WorkoutTemplate? =
        programDao.getCurrentTemplate()?.toDomain()

    override fun observeCurrentTrainingWeek(): Flow<TrainingWeek?> =
        programDao.observeCurrentWeek().map { it?.toDomain() }

    override suspend fun getWorkoutTemplate(id: Long): WorkoutTemplate? =
        programDao.getTemplate(id)?.toDomain()

    override suspend fun getWorkoutTemplateAfter(sequenceNumber: Int): WorkoutTemplate? =
        programDao.getTemplateAfter(sequenceNumber)?.toDomain()

    override suspend fun setCurrentWorkoutTemplate(templateId: Long?) {
        val program = programDao.getActiveProgramRow() ?: return
        programDao.upsertProgress(
            ProgramProgressEntity(programId = program.id, currentWorkoutTemplateId = templateId),
        )
    }
}
