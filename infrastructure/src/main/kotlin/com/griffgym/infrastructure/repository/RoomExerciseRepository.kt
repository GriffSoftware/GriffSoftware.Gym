package com.griffgym.infrastructure.repository

import com.griffgym.domain.model.Exercise
import com.griffgym.domain.repository.ExerciseRepository
import com.griffgym.infrastructure.database.dao.ExerciseDao
import com.griffgym.infrastructure.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
) : ExerciseRepository {

    override fun observeExercises(): Flow<List<Exercise>> =
        exerciseDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getExercise(id: Long): Exercise? = exerciseDao.getById(id)?.toDomain()
}
