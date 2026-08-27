package com.griffgym.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.griffgym.infrastructure.database.entity.ExerciseEntity
import com.griffgym.infrastructure.database.entity.ExerciseLogEntity
import com.griffgym.infrastructure.database.entity.ExerciseTemplateEntity
import com.griffgym.infrastructure.database.entity.PlannedSetEntity
import com.griffgym.infrastructure.database.entity.ProgramProgressEntity
import com.griffgym.infrastructure.database.entity.ReferenceMaxEntity
import com.griffgym.infrastructure.database.entity.SetLogEntity
import com.griffgym.infrastructure.database.entity.TrainingCycleEntity
import com.griffgym.infrastructure.database.entity.TrainingProgramEntity
import com.griffgym.infrastructure.database.entity.TrainingWeekEntity
import com.griffgym.infrastructure.database.entity.WorkoutSessionEntity
import com.griffgym.infrastructure.database.entity.WorkoutTemplateEntity

/**
 * Bulk reads and writes over the whole training database, for backup and restore.
 *
 * Separate from the feature DAOs on purpose. Those exist to answer a screen's question and
 * are shaped for it; these exist to move a lifter's entire history in one direction or the
 * other, and mixing the two would put "delete everything" within reach of code that only
 * wanted to draw a list.
 */
@Dao
interface CloudSyncDao {

    // --- reading everything, for a backup ---------------------------------------------------

    @Query("SELECT * FROM exercise ORDER BY id")
    suspend fun allExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM reference_max")
    suspend fun allReferenceMaxes(): List<ReferenceMaxEntity>

    @Query("SELECT * FROM training_cycle ORDER BY cycleNumber")
    suspend fun allCycles(): List<TrainingCycleEntity>

    @Query("SELECT * FROM training_program ORDER BY id")
    suspend fun allPrograms(): List<TrainingProgramEntity>

    @Query("SELECT * FROM training_week ORDER BY programId, weekNumber")
    suspend fun allWeeks(): List<TrainingWeekEntity>

    @Query("SELECT * FROM workout_template ORDER BY weekId, dayNumber")
    suspend fun allWorkoutTemplates(): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM exercise_template ORDER BY workoutTemplateId, position")
    suspend fun allExerciseTemplates(): List<ExerciseTemplateEntity>

    @Query("SELECT * FROM planned_set ORDER BY exerciseTemplateId, position")
    suspend fun allPlannedSets(): List<PlannedSetEntity>

    @Query("SELECT * FROM program_progress")
    suspend fun allProgramProgress(): List<ProgramProgressEntity>

    @Query("SELECT * FROM workout_session ORDER BY startedAt")
    suspend fun allWorkoutSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM exercise_log ORDER BY sessionId, position")
    suspend fun allExerciseLogs(): List<ExerciseLogEntity>

    @Query("SELECT * FROM set_log ORDER BY exerciseLogId, position")
    suspend fun allSetLogs(): List<SetLogEntity>

    // --- writing, for a restore ---------------------------------------------------------------
    //
    // Each insert returns its generated row id, which is what lets the writer build the
    // sync id -> row id map it needs to wire the next level of the tree together.

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert
    suspend fun insertReferenceMaxes(referenceMaxes: List<ReferenceMaxEntity>)

    @Insert
    suspend fun insertCycle(cycle: TrainingCycleEntity): Long

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

    @Insert
    suspend fun insertProgramProgress(progress: ProgramProgressEntity)

    @Insert
    suspend fun insertWorkoutSession(session: WorkoutSessionEntity): Long

    @Insert
    suspend fun insertExerciseLog(log: ExerciseLogEntity): Long

    @Insert
    suspend fun insertSetLogs(sets: List<SetLogEntity>)

    // --- clearing -----------------------------------------------------------------------------
    //
    // Ordered child-first. Foreign keys are enforced inside a Room transaction, so deleting a
    // parent while its children are still there fails — and the order below is the one place
    // that has to know the shape of the graph.

    @Query("DELETE FROM set_log")
    suspend fun deleteAllSetLogs()

    @Query("DELETE FROM exercise_log")
    suspend fun deleteAllExerciseLogs()

    @Query("DELETE FROM workout_session")
    suspend fun deleteAllWorkoutSessions()

    @Query("DELETE FROM program_progress")
    suspend fun deleteAllProgramProgress()

    @Query("DELETE FROM planned_set")
    suspend fun deleteAllPlannedSets()

    @Query("DELETE FROM exercise_template")
    suspend fun deleteAllExerciseTemplates()

    @Query("DELETE FROM workout_template")
    suspend fun deleteAllWorkoutTemplates()

    @Query("DELETE FROM training_week")
    suspend fun deleteAllWeeks()

    @Query("DELETE FROM training_program")
    suspend fun deleteAllPrograms()

    @Query("DELETE FROM training_cycle")
    suspend fun deleteAllCycles()

    @Query("DELETE FROM reference_max")
    suspend fun deleteAllReferenceMaxes()

    @Query("DELETE FROM exercise")
    suspend fun deleteAllExercises()
}
