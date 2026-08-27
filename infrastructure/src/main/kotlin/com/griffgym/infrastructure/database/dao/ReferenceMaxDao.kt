package com.griffgym.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.infrastructure.database.entity.ReferenceMaxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceMaxDao {

    @Query("SELECT * FROM reference_max")
    fun observeAll(): Flow<List<ReferenceMaxEntity>>

    @Query("SELECT * FROM reference_max WHERE category = :category")
    suspend fun getByCategory(category: ExerciseCategory): ReferenceMaxEntity?

    @Query("SELECT * FROM reference_max")
    suspend fun observeAllOnce(): List<ReferenceMaxEntity>

    @Query("SELECT COUNT(*) FROM reference_max")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(referenceMax: ReferenceMaxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(referenceMaxes: List<ReferenceMaxEntity>)
}
