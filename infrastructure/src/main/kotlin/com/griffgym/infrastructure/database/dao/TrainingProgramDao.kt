package com.griffgym.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.griffgym.infrastructure.database.entity.ExerciseTemplateEntity
import com.griffgym.infrastructure.database.entity.PlannedSetEntity
import com.griffgym.infrastructure.database.entity.ProgramProgressEntity
import com.griffgym.infrastructure.database.entity.TrainingProgramEntity
import com.griffgym.infrastructure.database.entity.TrainingWeekEntity
import com.griffgym.infrastructure.database.entity.WorkoutTemplateEntity
import com.griffgym.infrastructure.database.relation.TrainingProgramWithWeeks
import com.griffgym.infrastructure.database.relation.TrainingWeekWithWorkouts
import com.griffgym.infrastructure.database.relation.WorkoutTemplateDetail
import kotlinx.coroutines.flow.Flow

private const val TEMPLATE_DETAIL_COLUMNS =
    "wt.*, w.weekNumber AS weekNumber, w.isDeload AS isDeload"

private const val TEMPLATE_DETAIL_FROM =
    "FROM workout_template wt JOIN training_week w ON w.id = wt.weekId"

@Dao
interface TrainingProgramDao {

    @Transaction
    @Query("SELECT * FROM training_program WHERE isActive = 1 LIMIT 1")
    fun observeActiveProgram(): Flow<TrainingProgramWithWeeks?>

    @Transaction
    @Query("SELECT * FROM training_program WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProgram(): TrainingProgramWithWeeks?

    @Query("SELECT * FROM training_program WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProgramRow(): TrainingProgramEntity?

    @Transaction
    @Query(
        "SELECT $TEMPLATE_DETAIL_COLUMNS $TEMPLATE_DETAIL_FROM " +
            "JOIN program_progress p ON p.currentWorkoutTemplateId = wt.id",
    )
    fun observeCurrentTemplate(): Flow<WorkoutTemplateDetail?>

    @Transaction
    @Query(
        "SELECT $TEMPLATE_DETAIL_COLUMNS $TEMPLATE_DETAIL_FROM " +
            "JOIN program_progress p ON p.currentWorkoutTemplateId = wt.id",
    )
    suspend fun getCurrentTemplate(): WorkoutTemplateDetail?

    @Transaction
    @Query("SELECT $TEMPLATE_DETAIL_COLUMNS $TEMPLATE_DETAIL_FROM WHERE wt.id = :id")
    suspend fun getTemplate(id: Long): WorkoutTemplateDetail?

    @Transaction
    @Query(
        "SELECT $TEMPLATE_DETAIL_COLUMNS $TEMPLATE_DETAIL_FROM " +
            "WHERE wt.sequenceNumber > :sequenceNumber ORDER BY wt.sequenceNumber LIMIT 1",
    )
    suspend fun getTemplateAfter(sequenceNumber: Int): WorkoutTemplateDetail?

    @Transaction
    @Query(
        "SELECT w.* FROM training_week w " +
            "JOIN workout_template wt ON wt.weekId = w.id " +
            "JOIN program_progress p ON p.currentWorkoutTemplateId = wt.id",
    )
    fun observeCurrentWeek(): Flow<TrainingWeekWithWorkouts?>

    @Query("SELECT * FROM program_progress WHERE programId = :programId")
    suspend fun getProgress(programId: Long): ProgramProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: ProgramProgressEntity)

    @Insert
    suspend fun insertProgram(program: TrainingProgramEntity): Long

    @Insert
    suspend fun insertWeek(week: TrainingWeekEntity): Long

    @Insert
    suspend fun insertWorkoutTemplate(template: WorkoutTemplateEntity): Long

    @Insert
    suspend fun insertExerciseTemplate(template: ExerciseTemplateEntity): Long

    @Insert
    suspend fun insertPlannedSets(sets: List<PlannedSetEntity>)

    @Query("SELECT COUNT(*) FROM training_program")
    suspend fun programCount(): Int
}
