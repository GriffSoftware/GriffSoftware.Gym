package com.griffgym.infrastructure.database

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.griffgym.infrastructure.database.dao.ExerciseDao
import com.griffgym.infrastructure.database.dao.ExerciseDao_Impl
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao_Impl
import com.griffgym.infrastructure.database.dao.TrainingProgramDao
import com.griffgym.infrastructure.database.dao.TrainingProgramDao_Impl
import com.griffgym.infrastructure.database.dao.WorkoutSessionDao
import com.griffgym.infrastructure.database.dao.WorkoutSessionDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class GriffGymDatabase_Impl : GriffGymDatabase() {
  private val _exerciseDao: Lazy<ExerciseDao> = lazy {
    ExerciseDao_Impl(this)
  }

  private val _trainingProgramDao: Lazy<TrainingProgramDao> = lazy {
    TrainingProgramDao_Impl(this)
  }

  private val _workoutSessionDao: Lazy<WorkoutSessionDao> = lazy {
    WorkoutSessionDao_Impl(this)
  }

  private val _referenceMaxDao: Lazy<ReferenceMaxDao> = lazy {
    ReferenceMaxDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "07d330b60a6da093bf65cbc3d523704f", "41defc84afc091f2537f054485ebb153") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `exercise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_name` ON `exercise` (`name`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_category` ON `exercise` (`category`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `training_program` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `training_week` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `programId` INTEGER NOT NULL, `weekNumber` INTEGER NOT NULL, `label` TEXT NOT NULL, `isDeload` INTEGER NOT NULL, FOREIGN KEY(`programId`) REFERENCES `training_program`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_training_week_programId_weekNumber` ON `training_week` (`programId`, `weekNumber`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `workout_template` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `weekId` INTEGER NOT NULL, `dayNumber` INTEGER NOT NULL, `sequenceNumber` INTEGER NOT NULL, `title` TEXT NOT NULL, FOREIGN KEY(`weekId`) REFERENCES `training_week`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_template_weekId_dayNumber` ON `workout_template` (`weekId`, `dayNumber`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_template_sequenceNumber` ON `workout_template` (`sequenceNumber`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `exercise_template` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutTemplateId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, `type` TEXT NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`workoutTemplateId`) REFERENCES `workout_template`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_template_workoutTemplateId` ON `exercise_template` (`workoutTemplateId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_template_exerciseId` ON `exercise_template` (`exerciseId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `planned_set` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exerciseTemplateId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `weightKg` REAL, `reps` INTEGER, `rpeMin` REAL, `rpeMax` REAL, FOREIGN KEY(`exerciseTemplateId`) REFERENCES `exercise_template`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_set_exerciseTemplateId` ON `planned_set` (`exerciseTemplateId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `program_progress` (`programId` INTEGER NOT NULL, `currentWorkoutTemplateId` INTEGER, PRIMARY KEY(`programId`), FOREIGN KEY(`programId`) REFERENCES `training_program`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`currentWorkoutTemplateId`) REFERENCES `workout_template`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_program_progress_currentWorkoutTemplateId` ON `program_progress` (`currentWorkoutTemplateId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `workout_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER, `weekNumber` INTEGER NOT NULL, `dayNumber` INTEGER NOT NULL, `title` TEXT NOT NULL, `isDeload` INTEGER NOT NULL, `status` TEXT NOT NULL, `date` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `totalVolumeKg` REAL, `notes` TEXT, FOREIGN KEY(`templateId`) REFERENCES `workout_template`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_templateId` ON `workout_session` (`templateId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_status` ON `workout_session` (`status`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_date` ON `workout_session` (`date`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `exercise_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, `type` TEXT NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `workout_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_log_sessionId` ON `exercise_log` (`sessionId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_log_exerciseId` ON `exercise_log` (`exerciseId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `set_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exerciseLogId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `plannedWeightKg` REAL, `plannedReps` INTEGER, `plannedRpeMin` REAL, `plannedRpeMax` REAL, `actualWeightKg` REAL, `actualReps` INTEGER, `actualRpe` REAL, `completed` INTEGER NOT NULL, `notes` TEXT, FOREIGN KEY(`exerciseLogId`) REFERENCES `exercise_log`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_set_log_exerciseLogId` ON `set_log` (`exerciseLogId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `reference_max` (`category` TEXT NOT NULL, `weightKg` REAL NOT NULL, `updatedOn` INTEGER NOT NULL, PRIMARY KEY(`category`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '07d330b60a6da093bf65cbc3d523704f')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `exercise`")
        connection.execSQL("DROP TABLE IF EXISTS `training_program`")
        connection.execSQL("DROP TABLE IF EXISTS `training_week`")
        connection.execSQL("DROP TABLE IF EXISTS `workout_template`")
        connection.execSQL("DROP TABLE IF EXISTS `exercise_template`")
        connection.execSQL("DROP TABLE IF EXISTS `planned_set`")
        connection.execSQL("DROP TABLE IF EXISTS `program_progress`")
        connection.execSQL("DROP TABLE IF EXISTS `workout_session`")
        connection.execSQL("DROP TABLE IF EXISTS `exercise_log`")
        connection.execSQL("DROP TABLE IF EXISTS `set_log`")
        connection.execSQL("DROP TABLE IF EXISTS `reference_max`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsExercise: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsExercise.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExercise.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExercise.put("category", TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysExercise: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesExercise: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesExercise.add(TableInfo.Index("index_exercise_name", true, listOf("name"), listOf("ASC")))
        _indicesExercise.add(TableInfo.Index("index_exercise_category", false, listOf("category"), listOf("ASC")))
        val _infoExercise: TableInfo = TableInfo("exercise", _columnsExercise, _foreignKeysExercise, _indicesExercise)
        val _existingExercise: TableInfo = read(connection, "exercise")
        if (!_infoExercise.equals(_existingExercise)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |exercise(com.griffgym.infrastructure.database.entity.ExerciseEntity).
              | Expected:
              |""".trimMargin() + _infoExercise + """
              |
              | Found:
              |""".trimMargin() + _existingExercise)
        }
        val _columnsTrainingProgram: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTrainingProgram.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTrainingProgram.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTrainingProgram.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTrainingProgram.put("isActive", TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTrainingProgram: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTrainingProgram: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoTrainingProgram: TableInfo = TableInfo("training_program", _columnsTrainingProgram, _foreignKeysTrainingProgram, _indicesTrainingProgram)
        val _existingTrainingProgram: TableInfo = read(connection, "training_program")
        if (!_infoTrainingProgram.equals(_existingTrainingProgram)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |training_program(com.griffgym.infrastructure.database.entity.TrainingProgramEntity).
              | Expected:
              |""".trimMargin() + _infoTrainingProgram + """
              |
              | Found:
              |""".trimMargin() + _existingTrainingProgram)
        }
        val _columnsTrainingWeek: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTrainingWeek.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTrainingWeek.put("programId", TableInfo.Column("programId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTrainingWeek.put("weekNumber", TableInfo.Column("weekNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTrainingWeek.put("label", TableInfo.Column("label", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTrainingWeek.put("isDeload", TableInfo.Column("isDeload", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTrainingWeek: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysTrainingWeek.add(TableInfo.ForeignKey("training_program", "CASCADE", "NO ACTION", listOf("programId"), listOf("id")))
        val _indicesTrainingWeek: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesTrainingWeek.add(TableInfo.Index("index_training_week_programId_weekNumber", true, listOf("programId", "weekNumber"), listOf("ASC", "ASC")))
        val _infoTrainingWeek: TableInfo = TableInfo("training_week", _columnsTrainingWeek, _foreignKeysTrainingWeek, _indicesTrainingWeek)
        val _existingTrainingWeek: TableInfo = read(connection, "training_week")
        if (!_infoTrainingWeek.equals(_existingTrainingWeek)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |training_week(com.griffgym.infrastructure.database.entity.TrainingWeekEntity).
              | Expected:
              |""".trimMargin() + _infoTrainingWeek + """
              |
              | Found:
              |""".trimMargin() + _existingTrainingWeek)
        }
        val _columnsWorkoutTemplate: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWorkoutTemplate.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutTemplate.put("weekId", TableInfo.Column("weekId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutTemplate.put("dayNumber", TableInfo.Column("dayNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutTemplate.put("sequenceNumber", TableInfo.Column("sequenceNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutTemplate.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWorkoutTemplate: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysWorkoutTemplate.add(TableInfo.ForeignKey("training_week", "CASCADE", "NO ACTION", listOf("weekId"), listOf("id")))
        val _indicesWorkoutTemplate: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesWorkoutTemplate.add(TableInfo.Index("index_workout_template_weekId_dayNumber", true, listOf("weekId", "dayNumber"), listOf("ASC", "ASC")))
        _indicesWorkoutTemplate.add(TableInfo.Index("index_workout_template_sequenceNumber", false, listOf("sequenceNumber"), listOf("ASC")))
        val _infoWorkoutTemplate: TableInfo = TableInfo("workout_template", _columnsWorkoutTemplate, _foreignKeysWorkoutTemplate, _indicesWorkoutTemplate)
        val _existingWorkoutTemplate: TableInfo = read(connection, "workout_template")
        if (!_infoWorkoutTemplate.equals(_existingWorkoutTemplate)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |workout_template(com.griffgym.infrastructure.database.entity.WorkoutTemplateEntity).
              | Expected:
              |""".trimMargin() + _infoWorkoutTemplate + """
              |
              | Found:
              |""".trimMargin() + _existingWorkoutTemplate)
        }
        val _columnsExerciseTemplate: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsExerciseTemplate.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExerciseTemplate.put("workoutTemplateId", TableInfo.Column("workoutTemplateId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExerciseTemplate.put("exerciseId", TableInfo.Column("exerciseId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExerciseTemplate.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExerciseTemplate.put("position", TableInfo.Column("position", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysExerciseTemplate: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysExerciseTemplate.add(TableInfo.ForeignKey("workout_template", "CASCADE", "NO ACTION", listOf("workoutTemplateId"), listOf("id")))
        _foreignKeysExerciseTemplate.add(TableInfo.ForeignKey("exercise", "RESTRICT", "NO ACTION", listOf("exerciseId"), listOf("id")))
        val _indicesExerciseTemplate: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesExerciseTemplate.add(TableInfo.Index("index_exercise_template_workoutTemplateId", false, listOf("workoutTemplateId"), listOf("ASC")))
        _indicesExerciseTemplate.add(TableInfo.Index("index_exercise_template_exerciseId", false, listOf("exerciseId"), listOf("ASC")))
        val _infoExerciseTemplate: TableInfo = TableInfo("exercise_template", _columnsExerciseTemplate, _foreignKeysExerciseTemplate, _indicesExerciseTemplate)
        val _existingExerciseTemplate: TableInfo = read(connection, "exercise_template")
        if (!_infoExerciseTemplate.equals(_existingExerciseTemplate)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |exercise_template(com.griffgym.infrastructure.database.entity.ExerciseTemplateEntity).
              | Expected:
              |""".trimMargin() + _infoExerciseTemplate + """
              |
              | Found:
              |""".trimMargin() + _existingExerciseTemplate)
        }
        val _columnsPlannedSet: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPlannedSet.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlannedSet.put("exerciseTemplateId", TableInfo.Column("exerciseTemplateId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlannedSet.put("position", TableInfo.Column("position", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlannedSet.put("weightKg", TableInfo.Column("weightKg", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlannedSet.put("reps", TableInfo.Column("reps", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlannedSet.put("rpeMin", TableInfo.Column("rpeMin", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlannedSet.put("rpeMax", TableInfo.Column("rpeMax", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPlannedSet: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysPlannedSet.add(TableInfo.ForeignKey("exercise_template", "CASCADE", "NO ACTION", listOf("exerciseTemplateId"), listOf("id")))
        val _indicesPlannedSet: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesPlannedSet.add(TableInfo.Index("index_planned_set_exerciseTemplateId", false, listOf("exerciseTemplateId"), listOf("ASC")))
        val _infoPlannedSet: TableInfo = TableInfo("planned_set", _columnsPlannedSet, _foreignKeysPlannedSet, _indicesPlannedSet)
        val _existingPlannedSet: TableInfo = read(connection, "planned_set")
        if (!_infoPlannedSet.equals(_existingPlannedSet)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |planned_set(com.griffgym.infrastructure.database.entity.PlannedSetEntity).
              | Expected:
              |""".trimMargin() + _infoPlannedSet + """
              |
              | Found:
              |""".trimMargin() + _existingPlannedSet)
        }
        val _columnsProgramProgress: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsProgramProgress.put("programId", TableInfo.Column("programId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProgramProgress.put("currentWorkoutTemplateId", TableInfo.Column("currentWorkoutTemplateId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProgramProgress: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysProgramProgress.add(TableInfo.ForeignKey("training_program", "CASCADE", "NO ACTION", listOf("programId"), listOf("id")))
        _foreignKeysProgramProgress.add(TableInfo.ForeignKey("workout_template", "SET NULL", "NO ACTION", listOf("currentWorkoutTemplateId"), listOf("id")))
        val _indicesProgramProgress: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesProgramProgress.add(TableInfo.Index("index_program_progress_currentWorkoutTemplateId", false, listOf("currentWorkoutTemplateId"), listOf("ASC")))
        val _infoProgramProgress: TableInfo = TableInfo("program_progress", _columnsProgramProgress, _foreignKeysProgramProgress, _indicesProgramProgress)
        val _existingProgramProgress: TableInfo = read(connection, "program_progress")
        if (!_infoProgramProgress.equals(_existingProgramProgress)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |program_progress(com.griffgym.infrastructure.database.entity.ProgramProgressEntity).
              | Expected:
              |""".trimMargin() + _infoProgramProgress + """
              |
              | Found:
              |""".trimMargin() + _existingProgramProgress)
        }
        val _columnsWorkoutSession: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWorkoutSession.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("templateId", TableInfo.Column("templateId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("weekNumber", TableInfo.Column("weekNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("dayNumber", TableInfo.Column("dayNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("isDeload", TableInfo.Column("isDeload", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("status", TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("date", TableInfo.Column("date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("startedAt", TableInfo.Column("startedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("finishedAt", TableInfo.Column("finishedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("totalVolumeKg", TableInfo.Column("totalVolumeKg", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWorkoutSession.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWorkoutSession: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysWorkoutSession.add(TableInfo.ForeignKey("workout_template", "SET NULL", "NO ACTION", listOf("templateId"), listOf("id")))
        val _indicesWorkoutSession: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesWorkoutSession.add(TableInfo.Index("index_workout_session_templateId", false, listOf("templateId"), listOf("ASC")))
        _indicesWorkoutSession.add(TableInfo.Index("index_workout_session_status", false, listOf("status"), listOf("ASC")))
        _indicesWorkoutSession.add(TableInfo.Index("index_workout_session_date", false, listOf("date"), listOf("ASC")))
        val _infoWorkoutSession: TableInfo = TableInfo("workout_session", _columnsWorkoutSession, _foreignKeysWorkoutSession, _indicesWorkoutSession)
        val _existingWorkoutSession: TableInfo = read(connection, "workout_session")
        if (!_infoWorkoutSession.equals(_existingWorkoutSession)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |workout_session(com.griffgym.infrastructure.database.entity.WorkoutSessionEntity).
              | Expected:
              |""".trimMargin() + _infoWorkoutSession + """
              |
              | Found:
              |""".trimMargin() + _existingWorkoutSession)
        }
        val _columnsExerciseLog: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsExerciseLog.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExerciseLog.put("sessionId", TableInfo.Column("sessionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExerciseLog.put("exerciseId", TableInfo.Column("exerciseId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExerciseLog.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExerciseLog.put("position", TableInfo.Column("position", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysExerciseLog: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysExerciseLog.add(TableInfo.ForeignKey("workout_session", "CASCADE", "NO ACTION", listOf("sessionId"), listOf("id")))
        _foreignKeysExerciseLog.add(TableInfo.ForeignKey("exercise", "RESTRICT", "NO ACTION", listOf("exerciseId"), listOf("id")))
        val _indicesExerciseLog: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesExerciseLog.add(TableInfo.Index("index_exercise_log_sessionId", false, listOf("sessionId"), listOf("ASC")))
        _indicesExerciseLog.add(TableInfo.Index("index_exercise_log_exerciseId", false, listOf("exerciseId"), listOf("ASC")))
        val _infoExerciseLog: TableInfo = TableInfo("exercise_log", _columnsExerciseLog, _foreignKeysExerciseLog, _indicesExerciseLog)
        val _existingExerciseLog: TableInfo = read(connection, "exercise_log")
        if (!_infoExerciseLog.equals(_existingExerciseLog)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |exercise_log(com.griffgym.infrastructure.database.entity.ExerciseLogEntity).
              | Expected:
              |""".trimMargin() + _infoExerciseLog + """
              |
              | Found:
              |""".trimMargin() + _existingExerciseLog)
        }
        val _columnsSetLog: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSetLog.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("exerciseLogId", TableInfo.Column("exerciseLogId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("position", TableInfo.Column("position", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("plannedWeightKg", TableInfo.Column("plannedWeightKg", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("plannedReps", TableInfo.Column("plannedReps", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("plannedRpeMin", TableInfo.Column("plannedRpeMin", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("plannedRpeMax", TableInfo.Column("plannedRpeMax", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("actualWeightKg", TableInfo.Column("actualWeightKg", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("actualReps", TableInfo.Column("actualReps", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("actualRpe", TableInfo.Column("actualRpe", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("completed", TableInfo.Column("completed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSetLog.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSetLog: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysSetLog.add(TableInfo.ForeignKey("exercise_log", "CASCADE", "NO ACTION", listOf("exerciseLogId"), listOf("id")))
        val _indicesSetLog: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesSetLog.add(TableInfo.Index("index_set_log_exerciseLogId", false, listOf("exerciseLogId"), listOf("ASC")))
        val _infoSetLog: TableInfo = TableInfo("set_log", _columnsSetLog, _foreignKeysSetLog, _indicesSetLog)
        val _existingSetLog: TableInfo = read(connection, "set_log")
        if (!_infoSetLog.equals(_existingSetLog)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |set_log(com.griffgym.infrastructure.database.entity.SetLogEntity).
              | Expected:
              |""".trimMargin() + _infoSetLog + """
              |
              | Found:
              |""".trimMargin() + _existingSetLog)
        }
        val _columnsReferenceMax: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsReferenceMax.put("category", TableInfo.Column("category", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsReferenceMax.put("weightKg", TableInfo.Column("weightKg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsReferenceMax.put("updatedOn", TableInfo.Column("updatedOn", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysReferenceMax: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesReferenceMax: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoReferenceMax: TableInfo = TableInfo("reference_max", _columnsReferenceMax, _foreignKeysReferenceMax, _indicesReferenceMax)
        val _existingReferenceMax: TableInfo = read(connection, "reference_max")
        if (!_infoReferenceMax.equals(_existingReferenceMax)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |reference_max(com.griffgym.infrastructure.database.entity.ReferenceMaxEntity).
              | Expected:
              |""".trimMargin() + _infoReferenceMax + """
              |
              | Found:
              |""".trimMargin() + _existingReferenceMax)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "exercise", "training_program", "training_week", "workout_template", "exercise_template", "planned_set", "program_progress", "workout_session", "exercise_log", "set_log", "reference_max")
  }

  public override fun clearAllTables() {
    super.performClear(true, "exercise", "training_program", "training_week", "workout_template", "exercise_template", "planned_set", "program_progress", "workout_session", "exercise_log", "set_log", "reference_max")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ExerciseDao::class, ExerciseDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(TrainingProgramDao::class, TrainingProgramDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(WorkoutSessionDao::class, WorkoutSessionDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ReferenceMaxDao::class, ReferenceMaxDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun exerciseDao(): ExerciseDao = _exerciseDao.value

  public override fun trainingProgramDao(): TrainingProgramDao = _trainingProgramDao.value

  public override fun workoutSessionDao(): WorkoutSessionDao = _workoutSessionDao.value

  public override fun referenceMaxDao(): ReferenceMaxDao = _referenceMaxDao.value
}
