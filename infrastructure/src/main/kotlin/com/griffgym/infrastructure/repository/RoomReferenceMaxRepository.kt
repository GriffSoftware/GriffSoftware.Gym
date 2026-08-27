package com.griffgym.infrastructure.repository

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.domain.model.Weight
import com.griffgym.domain.repository.ReferenceMaxRepository
import com.griffgym.infrastructure.database.entity.SyncEntityType
import com.griffgym.infrastructure.database.entity.newSyncId
import com.griffgym.infrastructure.sync.NoOpSyncRecorder
import com.griffgym.infrastructure.sync.SyncRecorder
import androidx.room.withTransaction
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao
import com.griffgym.infrastructure.mapper.toDomain
import com.griffgym.infrastructure.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReferenceMaxRepository @Inject constructor(
    private val database: GriffGymDatabase,
    private val referenceMaxDao: ReferenceMaxDao,
    private val syncRecorder: SyncRecorder = NoOpSyncRecorder,
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
        database.withTransaction {
            // Reuses the existing row's sync id where there is one, so the server sees an
            // update to the squat max it already knows rather than a second, competing record.
            val syncId = referenceMaxDao.getByCategory(category)?.syncId ?: newSyncId()

            referenceMaxDao.upsert(
                ReferenceMax(category, weight, updatedOn).toEntity().copy(syncId = syncId),
            )

            // Same transaction as the write, so the two cannot disagree about whether this
            // number still needs sending.
            syncRecorder.markPending(SyncEntityType.REFERENCE_MAX, syncId)
        }

        syncRecorder.requestSync()
    }

    override suspend fun hasAnyReferenceMax(): Boolean = referenceMaxDao.count() > 0
}
