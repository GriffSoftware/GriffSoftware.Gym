package com.griffgym.infrastructure.repository

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.domain.model.Weight
import com.griffgym.domain.repository.ReferenceMaxRepository
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao
import com.griffgym.infrastructure.database.entity.ReferenceMaxEntity
import com.griffgym.infrastructure.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReferenceMaxRepository @Inject constructor(
    private val referenceMaxDao: ReferenceMaxDao,
) : ReferenceMaxRepository {

    override fun observeReferenceMaxes(): Flow<List<ReferenceMax>> =
        referenceMaxDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getReferenceMax(category: ExerciseCategory): ReferenceMax? =
        referenceMaxDao.getByCategory(category)?.toDomain()

    override suspend fun updateReferenceMax(
        category: ExerciseCategory,
        weight: Weight,
        updatedOn: LocalDate,
    ) {
        referenceMaxDao.upsert(
            ReferenceMaxEntity(
                category = category,
                weightKg = weight.kilograms,
                updatedOn = updatedOn.toEpochDay(),
            ),
        )
    }
}
