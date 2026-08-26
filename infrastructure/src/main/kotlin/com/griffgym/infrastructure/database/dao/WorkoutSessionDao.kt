package com.griffgym.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.griffgym.domain.model.WorkoutStatus
import com.griffgym.infrastructure.database.entity.ExerciseLogEntity
import com.griffgym.infrastructure.database.entity.SetLogEntity
import com.griffgym.infrastructure.database.entity.WorkoutSessionEntity
import com.griffgym.infrastructure.database.relation.WorkoutSessionWithExercises
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WorkoutSessionDao {

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    fun observeByStatus(status: WorkoutStatus): Flow<WorkoutSessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    suspend fun getByStatus(status: WorkoutStatus): WorkoutSessionWithExercises?

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observeById(id: Long): Flow<WorkoutSessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSessionWithExercises?

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status != 'IN_PROGRESS' ORDER BY date DESC, startedAt DESC")
    fun observeHistory(): Flow<List<WorkoutSessionWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'COMPLETED' ORDER BY date ASC, startedAt ASC")
    fun observeCompleted(): Flow<List<WorkoutSessionWithExercises>>

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Insert
    suspend fun insertExerciseLog(log: ExerciseLogEntity): Long

    @Insert
    suspend fun insertSetLogs(sets: List<SetLogEntity>)

    @Insert
    suspend fun insertSetLog(set: SetLogEntity): Long

    @Query("SELECT * FROM set_log WHERE id = :id")
    suspend fun getSetLog(id: Long): SetLogEntity?

    @Query("SELECT * FROM set_log WHERE exerciseLogId = :exerciseLogId ORDER BY position DESC LIMIT 1")
    suspend fun lastSetOf(exerciseLogId: Long): SetLogEntity?

    @Query(
        "UPDATE set_log SET actualWeightKg = :weightKg, actualReps = :reps, actualRpe = :rpe, " +
            "completed = :completed, notes = :notes WHERE id = :id",
    )
    suspend fun updateSetResult(
        id: Long,
        weightKg: Double?,
        reps: Int?,
        rpe: Double?,
        completed: Boolean,
        notes: String?,
    )

    @Query("DELETE FROM set_log WHERE id = :id")
    suspend fun deleteSetLog(id: Long)

    @Query("SELECT COALESCE(MAX(position), 0) FROM set_log WHERE exerciseLogId = :exerciseLogId")
    suspend fun lastSetPosition(exerciseLogId: Long): Int

    @Query("SELECT COALESCE(MAX(position), 0) FROM exercise_log WHERE sessionId = :sessionId")
    suspend fun lastExercisePosition(sessionId: Long): Int

    @Query(
        "UPDATE workout_session SET status = :status, finishedAt = :finishedAt, " +
            "totalVolumeKg = :totalVolumeKg WHERE id = :id",
    )
    suspend fun finishSession(
        id: Long,
        status: WorkoutStatus,
        finishedAt: Instant,
        totalVolumeKg: Double?,
    )

    @Query("UPDATE workout_session SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?)
}
