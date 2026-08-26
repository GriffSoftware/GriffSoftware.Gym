package com.griffgym.infrastructure.database.dao

import androidx.collection.LongSparseArray
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndex
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.room.util.recursiveFetchLongSparseArray
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.infrastructure.database.converter.GriffGymConverters
import com.griffgym.infrastructure.database.entity.ExerciseEntity
import com.griffgym.infrastructure.database.entity.ExerciseTemplateEntity
import com.griffgym.infrastructure.database.entity.PlannedSetEntity
import com.griffgym.infrastructure.database.entity.ProgramProgressEntity
import com.griffgym.infrastructure.database.entity.TrainingProgramEntity
import com.griffgym.infrastructure.database.entity.TrainingWeekEntity
import com.griffgym.infrastructure.database.entity.WorkoutTemplateEntity
import com.griffgym.infrastructure.database.relation.ExerciseTemplateWithDetails
import com.griffgym.infrastructure.database.relation.TrainingProgramWithWeeks
import com.griffgym.infrastructure.database.relation.TrainingWeekWithWorkouts
import com.griffgym.infrastructure.database.relation.WorkoutTemplateDetail
import com.griffgym.infrastructure.database.relation.WorkoutTemplateWithExercises
import java.time.Instant
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class TrainingProgramDao_Impl(
  __db: RoomDatabase,
) : TrainingProgramDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfProgramProgressEntity: EntityInsertAdapter<ProgramProgressEntity>

  private val __insertAdapterOfTrainingProgramEntity: EntityInsertAdapter<TrainingProgramEntity>

  private val __griffGymConverters: GriffGymConverters = GriffGymConverters()

  private val __insertAdapterOfTrainingWeekEntity: EntityInsertAdapter<TrainingWeekEntity>

  private val __insertAdapterOfWorkoutTemplateEntity: EntityInsertAdapter<WorkoutTemplateEntity>

  private val __insertAdapterOfExerciseTemplateEntity: EntityInsertAdapter<ExerciseTemplateEntity>

  private val __insertAdapterOfPlannedSetEntity: EntityInsertAdapter<PlannedSetEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfProgramProgressEntity = object : EntityInsertAdapter<ProgramProgressEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `program_progress` (`programId`,`currentWorkoutTemplateId`) VALUES (?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ProgramProgressEntity) {
        statement.bindLong(1, entity.programId)
        val _tmpCurrentWorkoutTemplateId: Long? = entity.currentWorkoutTemplateId
        if (_tmpCurrentWorkoutTemplateId == null) {
          statement.bindNull(2)
        } else {
          statement.bindLong(2, _tmpCurrentWorkoutTemplateId)
        }
      }
    }
    this.__insertAdapterOfTrainingProgramEntity = object : EntityInsertAdapter<TrainingProgramEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `training_program` (`id`,`name`,`createdAt`,`isActive`) VALUES (nullif(?, 0),?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TrainingProgramEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        val _tmp: Long? = __griffGymConverters.instantToEpochMillis(entity.createdAt)
        if (_tmp == null) {
          statement.bindNull(3)
        } else {
          statement.bindLong(3, _tmp)
        }
        val _tmp_1: Int = if (entity.isActive) 1 else 0
        statement.bindLong(4, _tmp_1.toLong())
      }
    }
    this.__insertAdapterOfTrainingWeekEntity = object : EntityInsertAdapter<TrainingWeekEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `training_week` (`id`,`programId`,`weekNumber`,`label`,`isDeload`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TrainingWeekEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.programId)
        statement.bindLong(3, entity.weekNumber.toLong())
        statement.bindText(4, entity.label)
        val _tmp: Int = if (entity.isDeload) 1 else 0
        statement.bindLong(5, _tmp.toLong())
      }
    }
    this.__insertAdapterOfWorkoutTemplateEntity = object : EntityInsertAdapter<WorkoutTemplateEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `workout_template` (`id`,`weekId`,`dayNumber`,`sequenceNumber`,`title`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WorkoutTemplateEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.weekId)
        statement.bindLong(3, entity.dayNumber.toLong())
        statement.bindLong(4, entity.sequenceNumber.toLong())
        statement.bindText(5, entity.title)
      }
    }
    this.__insertAdapterOfExerciseTemplateEntity = object : EntityInsertAdapter<ExerciseTemplateEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `exercise_template` (`id`,`workoutTemplateId`,`exerciseId`,`type`,`position`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ExerciseTemplateEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.workoutTemplateId)
        statement.bindLong(3, entity.exerciseId)
        val _tmp: String = __griffGymConverters.exerciseTypeToString(entity.type)
        statement.bindText(4, _tmp)
        statement.bindLong(5, entity.position.toLong())
      }
    }
    this.__insertAdapterOfPlannedSetEntity = object : EntityInsertAdapter<PlannedSetEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `planned_set` (`id`,`exerciseTemplateId`,`position`,`weightKg`,`reps`,`rpeMin`,`rpeMax`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PlannedSetEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.exerciseTemplateId)
        statement.bindLong(3, entity.position.toLong())
        val _tmpWeightKg: Double? = entity.weightKg
        if (_tmpWeightKg == null) {
          statement.bindNull(4)
        } else {
          statement.bindDouble(4, _tmpWeightKg)
        }
        val _tmpReps: Int? = entity.reps
        if (_tmpReps == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpReps.toLong())
        }
        val _tmpRpeMin: Double? = entity.rpeMin
        if (_tmpRpeMin == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpRpeMin)
        }
        val _tmpRpeMax: Double? = entity.rpeMax
        if (_tmpRpeMax == null) {
          statement.bindNull(7)
        } else {
          statement.bindDouble(7, _tmpRpeMax)
        }
      }
    }
  }

  public override suspend fun upsertProgress(progress: ProgramProgressEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfProgramProgressEntity.insert(_connection, progress)
  }

  public override suspend fun insertProgram(program: TrainingProgramEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfTrainingProgramEntity.insertAndReturnId(_connection, program)
    _result
  }

  public override suspend fun insertWeek(week: TrainingWeekEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfTrainingWeekEntity.insertAndReturnId(_connection, week)
    _result
  }

  public override suspend fun insertWorkoutTemplate(template: WorkoutTemplateEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfWorkoutTemplateEntity.insertAndReturnId(_connection, template)
    _result
  }

  public override suspend fun insertExerciseTemplate(template: ExerciseTemplateEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfExerciseTemplateEntity.insertAndReturnId(_connection, template)
    _result
  }

  public override suspend fun insertPlannedSets(sets: List<PlannedSetEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPlannedSetEntity.insert(_connection, sets)
  }

  public override fun observeActiveProgram(): Flow<TrainingProgramWithWeeks?> {
    val _sql: String = "SELECT * FROM training_program WHERE isActive = 1 LIMIT 1"
    return createFlow(__db, true, arrayOf("exercise", "planned_set", "exercise_template", "workout_template", "training_week", "training_program")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "isActive")
        val _collectionWeeks: LongSparseArray<MutableList<TrainingWeekWithWorkouts>> = LongSparseArray<MutableList<TrainingWeekWithWorkouts>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionWeeks.containsKey(_tmpKey)) {
            _collectionWeeks.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshiptrainingWeekAscomGriffgymInfrastructureDatabaseRelationTrainingWeekWithWorkouts(_connection, _collectionWeeks)
        val _result: TrainingProgramWithWeeks?
        if (_stmt.step()) {
          val _tmpProgram: TrainingProgramEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCreatedAt: Instant
          val _tmp: Long?
          if (_stmt.isNull(_columnIndexOfCreatedAt)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfCreatedAt)
          }
          val _tmp_1: Instant? = __griffGymConverters.epochMillisToInstant(_tmp)
          if (_tmp_1 == null) {
            error("Expected NON-NULL 'java.time.Instant', but it was NULL.")
          } else {
            _tmpCreatedAt = _tmp_1
          }
          val _tmpIsActive: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = _tmp_2 != 0
          _tmpProgram = TrainingProgramEntity(_tmpId,_tmpName,_tmpCreatedAt,_tmpIsActive)
          val _tmpWeeksCollection: MutableList<TrainingWeekWithWorkouts>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpWeeksCollection = checkNotNull(_collectionWeeks.get(_tmpKey_1))
          _result = TrainingProgramWithWeeks(_tmpProgram,_tmpWeeksCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getActiveProgram(): TrainingProgramWithWeeks? {
    val _sql: String = "SELECT * FROM training_program WHERE isActive = 1 LIMIT 1"
    return performSuspending(__db, true, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "isActive")
        val _collectionWeeks: LongSparseArray<MutableList<TrainingWeekWithWorkouts>> = LongSparseArray<MutableList<TrainingWeekWithWorkouts>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionWeeks.containsKey(_tmpKey)) {
            _collectionWeeks.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshiptrainingWeekAscomGriffgymInfrastructureDatabaseRelationTrainingWeekWithWorkouts(_connection, _collectionWeeks)
        val _result: TrainingProgramWithWeeks?
        if (_stmt.step()) {
          val _tmpProgram: TrainingProgramEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCreatedAt: Instant
          val _tmp: Long?
          if (_stmt.isNull(_columnIndexOfCreatedAt)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfCreatedAt)
          }
          val _tmp_1: Instant? = __griffGymConverters.epochMillisToInstant(_tmp)
          if (_tmp_1 == null) {
            error("Expected NON-NULL 'java.time.Instant', but it was NULL.")
          } else {
            _tmpCreatedAt = _tmp_1
          }
          val _tmpIsActive: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = _tmp_2 != 0
          _tmpProgram = TrainingProgramEntity(_tmpId,_tmpName,_tmpCreatedAt,_tmpIsActive)
          val _tmpWeeksCollection: MutableList<TrainingWeekWithWorkouts>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpWeeksCollection = checkNotNull(_collectionWeeks.get(_tmpKey_1))
          _result = TrainingProgramWithWeeks(_tmpProgram,_tmpWeeksCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getActiveProgramRow(): TrainingProgramEntity? {
    val _sql: String = "SELECT * FROM training_program WHERE isActive = 1 LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "isActive")
        val _result: TrainingProgramEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCreatedAt: Instant
          val _tmp: Long?
          if (_stmt.isNull(_columnIndexOfCreatedAt)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfCreatedAt)
          }
          val _tmp_1: Instant? = __griffGymConverters.epochMillisToInstant(_tmp)
          if (_tmp_1 == null) {
            error("Expected NON-NULL 'java.time.Instant', but it was NULL.")
          } else {
            _tmpCreatedAt = _tmp_1
          }
          val _tmpIsActive: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = _tmp_2 != 0
          _result = TrainingProgramEntity(_tmpId,_tmpName,_tmpCreatedAt,_tmpIsActive)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeCurrentTemplate(): Flow<WorkoutTemplateDetail?> {
    val _sql: String = "SELECT wt.*, w.weekNumber AS weekNumber, w.isDeload AS isDeload FROM workout_template wt JOIN training_week w ON w.id = wt.weekId JOIN program_progress p ON p.currentWorkoutTemplateId = wt.id"
    return createFlow(__db, true, arrayOf("exercise", "planned_set", "exercise_template", "workout_template", "training_week", "program_progress")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfWeekId: Int = getColumnIndexOrThrow(_stmt, "weekId")
        val _columnIndexOfDayNumber: Int = getColumnIndexOrThrow(_stmt, "dayNumber")
        val _columnIndexOfSequenceNumber: Int = getColumnIndexOrThrow(_stmt, "sequenceNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _collectionExercises: LongSparseArray<MutableList<ExerciseTemplateWithDetails>> = LongSparseArray<MutableList<ExerciseTemplateWithDetails>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionExercises.containsKey(_tmpKey)) {
            _collectionExercises.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipexerciseTemplateAscomGriffgymInfrastructureDatabaseRelationExerciseTemplateWithDetails(_connection, _collectionExercises)
        val _result: WorkoutTemplateDetail?
        if (_stmt.step()) {
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpIsDeload: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp != 0
          val _tmpTemplate: WorkoutTemplateEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpWeekId: Long
          _tmpWeekId = _stmt.getLong(_columnIndexOfWeekId)
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpSequenceNumber: Int
          _tmpSequenceNumber = _stmt.getLong(_columnIndexOfSequenceNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          _tmpTemplate = WorkoutTemplateEntity(_tmpId,_tmpWeekId,_tmpDayNumber,_tmpSequenceNumber,_tmpTitle)
          val _tmpExercisesCollection: MutableList<ExerciseTemplateWithDetails>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_1))
          _result = WorkoutTemplateDetail(_tmpTemplate,_tmpWeekNumber,_tmpIsDeload,_tmpExercisesCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getCurrentTemplate(): WorkoutTemplateDetail? {
    val _sql: String = "SELECT wt.*, w.weekNumber AS weekNumber, w.isDeload AS isDeload FROM workout_template wt JOIN training_week w ON w.id = wt.weekId JOIN program_progress p ON p.currentWorkoutTemplateId = wt.id"
    return performSuspending(__db, true, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfWeekId: Int = getColumnIndexOrThrow(_stmt, "weekId")
        val _columnIndexOfDayNumber: Int = getColumnIndexOrThrow(_stmt, "dayNumber")
        val _columnIndexOfSequenceNumber: Int = getColumnIndexOrThrow(_stmt, "sequenceNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _collectionExercises: LongSparseArray<MutableList<ExerciseTemplateWithDetails>> = LongSparseArray<MutableList<ExerciseTemplateWithDetails>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionExercises.containsKey(_tmpKey)) {
            _collectionExercises.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipexerciseTemplateAscomGriffgymInfrastructureDatabaseRelationExerciseTemplateWithDetails(_connection, _collectionExercises)
        val _result: WorkoutTemplateDetail?
        if (_stmt.step()) {
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpIsDeload: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp != 0
          val _tmpTemplate: WorkoutTemplateEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpWeekId: Long
          _tmpWeekId = _stmt.getLong(_columnIndexOfWeekId)
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpSequenceNumber: Int
          _tmpSequenceNumber = _stmt.getLong(_columnIndexOfSequenceNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          _tmpTemplate = WorkoutTemplateEntity(_tmpId,_tmpWeekId,_tmpDayNumber,_tmpSequenceNumber,_tmpTitle)
          val _tmpExercisesCollection: MutableList<ExerciseTemplateWithDetails>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_1))
          _result = WorkoutTemplateDetail(_tmpTemplate,_tmpWeekNumber,_tmpIsDeload,_tmpExercisesCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTemplate(id: Long): WorkoutTemplateDetail? {
    val _sql: String = "SELECT wt.*, w.weekNumber AS weekNumber, w.isDeload AS isDeload FROM workout_template wt JOIN training_week w ON w.id = wt.weekId WHERE wt.id = ?"
    return performSuspending(__db, true, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfWeekId: Int = getColumnIndexOrThrow(_stmt, "weekId")
        val _columnIndexOfDayNumber: Int = getColumnIndexOrThrow(_stmt, "dayNumber")
        val _columnIndexOfSequenceNumber: Int = getColumnIndexOrThrow(_stmt, "sequenceNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _collectionExercises: LongSparseArray<MutableList<ExerciseTemplateWithDetails>> = LongSparseArray<MutableList<ExerciseTemplateWithDetails>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionExercises.containsKey(_tmpKey)) {
            _collectionExercises.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipexerciseTemplateAscomGriffgymInfrastructureDatabaseRelationExerciseTemplateWithDetails(_connection, _collectionExercises)
        val _result: WorkoutTemplateDetail?
        if (_stmt.step()) {
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpIsDeload: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp != 0
          val _tmpTemplate: WorkoutTemplateEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpWeekId: Long
          _tmpWeekId = _stmt.getLong(_columnIndexOfWeekId)
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpSequenceNumber: Int
          _tmpSequenceNumber = _stmt.getLong(_columnIndexOfSequenceNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          _tmpTemplate = WorkoutTemplateEntity(_tmpId,_tmpWeekId,_tmpDayNumber,_tmpSequenceNumber,_tmpTitle)
          val _tmpExercisesCollection: MutableList<ExerciseTemplateWithDetails>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_1))
          _result = WorkoutTemplateDetail(_tmpTemplate,_tmpWeekNumber,_tmpIsDeload,_tmpExercisesCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTemplateAfter(sequenceNumber: Int): WorkoutTemplateDetail? {
    val _sql: String = "SELECT wt.*, w.weekNumber AS weekNumber, w.isDeload AS isDeload FROM workout_template wt JOIN training_week w ON w.id = wt.weekId WHERE wt.sequenceNumber > ? ORDER BY wt.sequenceNumber LIMIT 1"
    return performSuspending(__db, true, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sequenceNumber.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfWeekId: Int = getColumnIndexOrThrow(_stmt, "weekId")
        val _columnIndexOfDayNumber: Int = getColumnIndexOrThrow(_stmt, "dayNumber")
        val _columnIndexOfSequenceNumber: Int = getColumnIndexOrThrow(_stmt, "sequenceNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _collectionExercises: LongSparseArray<MutableList<ExerciseTemplateWithDetails>> = LongSparseArray<MutableList<ExerciseTemplateWithDetails>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionExercises.containsKey(_tmpKey)) {
            _collectionExercises.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipexerciseTemplateAscomGriffgymInfrastructureDatabaseRelationExerciseTemplateWithDetails(_connection, _collectionExercises)
        val _result: WorkoutTemplateDetail?
        if (_stmt.step()) {
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpIsDeload: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp != 0
          val _tmpTemplate: WorkoutTemplateEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpWeekId: Long
          _tmpWeekId = _stmt.getLong(_columnIndexOfWeekId)
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpSequenceNumber: Int
          _tmpSequenceNumber = _stmt.getLong(_columnIndexOfSequenceNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          _tmpTemplate = WorkoutTemplateEntity(_tmpId,_tmpWeekId,_tmpDayNumber,_tmpSequenceNumber,_tmpTitle)
          val _tmpExercisesCollection: MutableList<ExerciseTemplateWithDetails>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_1))
          _result = WorkoutTemplateDetail(_tmpTemplate,_tmpWeekNumber,_tmpIsDeload,_tmpExercisesCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeCurrentWeek(): Flow<TrainingWeekWithWorkouts?> {
    val _sql: String = "SELECT w.* FROM training_week w JOIN workout_template wt ON wt.weekId = w.id JOIN program_progress p ON p.currentWorkoutTemplateId = wt.id"
    return createFlow(__db, true, arrayOf("exercise", "planned_set", "exercise_template", "workout_template", "training_week", "program_progress")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProgramId: Int = getColumnIndexOrThrow(_stmt, "programId")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _collectionWorkouts: LongSparseArray<MutableList<WorkoutTemplateWithExercises>> = LongSparseArray<MutableList<WorkoutTemplateWithExercises>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionWorkouts.containsKey(_tmpKey)) {
            _collectionWorkouts.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipworkoutTemplateAscomGriffgymInfrastructureDatabaseRelationWorkoutTemplateWithExercises(_connection, _collectionWorkouts)
        val _result: TrainingWeekWithWorkouts?
        if (_stmt.step()) {
          val _tmpWeek: TrainingWeekEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProgramId: Long
          _tmpProgramId = _stmt.getLong(_columnIndexOfProgramId)
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpLabel: String
          _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          val _tmpIsDeload: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp != 0
          _tmpWeek = TrainingWeekEntity(_tmpId,_tmpProgramId,_tmpWeekNumber,_tmpLabel,_tmpIsDeload)
          val _tmpWorkoutsCollection: MutableList<WorkoutTemplateWithExercises>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpWorkoutsCollection = checkNotNull(_collectionWorkouts.get(_tmpKey_1))
          _result = TrainingWeekWithWorkouts(_tmpWeek,_tmpWorkoutsCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getProgress(programId: Long): ProgramProgressEntity? {
    val _sql: String = "SELECT * FROM program_progress WHERE programId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, programId)
        val _columnIndexOfProgramId: Int = getColumnIndexOrThrow(_stmt, "programId")
        val _columnIndexOfCurrentWorkoutTemplateId: Int = getColumnIndexOrThrow(_stmt, "currentWorkoutTemplateId")
        val _result: ProgramProgressEntity?
        if (_stmt.step()) {
          val _tmpProgramId: Long
          _tmpProgramId = _stmt.getLong(_columnIndexOfProgramId)
          val _tmpCurrentWorkoutTemplateId: Long?
          if (_stmt.isNull(_columnIndexOfCurrentWorkoutTemplateId)) {
            _tmpCurrentWorkoutTemplateId = null
          } else {
            _tmpCurrentWorkoutTemplateId = _stmt.getLong(_columnIndexOfCurrentWorkoutTemplateId)
          }
          _result = ProgramProgressEntity(_tmpProgramId,_tmpCurrentWorkoutTemplateId)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun programCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM training_program"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  private fun __fetchRelationshipexerciseAscomGriffgymInfrastructureDatabaseEntityExerciseEntity(_connection: SQLiteConnection, _map: LongSparseArray<ExerciseEntity?>) {
    if (_map.isEmpty()) {
      return
    }
    if (_map.size() > 999) {
      recursiveFetchLongSparseArray(_map, false) { _tmpMap ->
        __fetchRelationshipexerciseAscomGriffgymInfrastructureDatabaseEntityExerciseEntity(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`name`,`category` FROM `exercise` WHERE `id` IN (")
    val _inputSize: Int = _map.size()
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (i in 0 until _map.size()) {
      val _item: Long = _map.keyAt(i)
      _stmt.bindLong(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "id")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfName: Int = 1
      val _columnIndexOfCategory: Int = 2
      while (_stmt.step()) {
        val _tmpKey: Long
        _tmpKey = _stmt.getLong(_itemKeyIndex)
        if (_map.containsKey(_tmpKey)) {
          val _item_1: ExerciseEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCategory: ExerciseCategory
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfCategory)
          _tmpCategory = __griffGymConverters.stringToExerciseCategory(_tmp)
          _item_1 = ExerciseEntity(_tmpId,_tmpName,_tmpCategory)
          _map.put(_tmpKey, _item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  private fun __fetchRelationshipplannedSetAscomGriffgymInfrastructureDatabaseEntityPlannedSetEntity(_connection: SQLiteConnection, _map: LongSparseArray<MutableList<PlannedSetEntity>>) {
    if (_map.isEmpty()) {
      return
    }
    if (_map.size() > 999) {
      recursiveFetchLongSparseArray(_map, true) { _tmpMap ->
        __fetchRelationshipplannedSetAscomGriffgymInfrastructureDatabaseEntityPlannedSetEntity(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`exerciseTemplateId`,`position`,`weightKg`,`reps`,`rpeMin`,`rpeMax` FROM `planned_set` WHERE `exerciseTemplateId` IN (")
    val _inputSize: Int = _map.size()
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (i in 0 until _map.size()) {
      val _item: Long = _map.keyAt(i)
      _stmt.bindLong(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "exerciseTemplateId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfExerciseTemplateId: Int = 1
      val _columnIndexOfPosition: Int = 2
      val _columnIndexOfWeightKg: Int = 3
      val _columnIndexOfReps: Int = 4
      val _columnIndexOfRpeMin: Int = 5
      val _columnIndexOfRpeMax: Int = 6
      while (_stmt.step()) {
        val _tmpKey: Long
        _tmpKey = _stmt.getLong(_itemKeyIndex)
        val _tmpRelation: MutableList<PlannedSetEntity>? = _map.get(_tmpKey)
        if (_tmpRelation != null) {
          val _item_1: PlannedSetEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpExerciseTemplateId: Long
          _tmpExerciseTemplateId = _stmt.getLong(_columnIndexOfExerciseTemplateId)
          val _tmpPosition: Int
          _tmpPosition = _stmt.getLong(_columnIndexOfPosition).toInt()
          val _tmpWeightKg: Double?
          if (_stmt.isNull(_columnIndexOfWeightKg)) {
            _tmpWeightKg = null
          } else {
            _tmpWeightKg = _stmt.getDouble(_columnIndexOfWeightKg)
          }
          val _tmpReps: Int?
          if (_stmt.isNull(_columnIndexOfReps)) {
            _tmpReps = null
          } else {
            _tmpReps = _stmt.getLong(_columnIndexOfReps).toInt()
          }
          val _tmpRpeMin: Double?
          if (_stmt.isNull(_columnIndexOfRpeMin)) {
            _tmpRpeMin = null
          } else {
            _tmpRpeMin = _stmt.getDouble(_columnIndexOfRpeMin)
          }
          val _tmpRpeMax: Double?
          if (_stmt.isNull(_columnIndexOfRpeMax)) {
            _tmpRpeMax = null
          } else {
            _tmpRpeMax = _stmt.getDouble(_columnIndexOfRpeMax)
          }
          _item_1 = PlannedSetEntity(_tmpId,_tmpExerciseTemplateId,_tmpPosition,_tmpWeightKg,_tmpReps,_tmpRpeMin,_tmpRpeMax)
          _tmpRelation.add(_item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  private fun __fetchRelationshipexerciseTemplateAscomGriffgymInfrastructureDatabaseRelationExerciseTemplateWithDetails(_connection: SQLiteConnection, _map: LongSparseArray<MutableList<ExerciseTemplateWithDetails>>) {
    if (_map.isEmpty()) {
      return
    }
    if (_map.size() > 999) {
      recursiveFetchLongSparseArray(_map, true) { _tmpMap ->
        __fetchRelationshipexerciseTemplateAscomGriffgymInfrastructureDatabaseRelationExerciseTemplateWithDetails(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`workoutTemplateId`,`exerciseId`,`type`,`position` FROM `exercise_template` WHERE `workoutTemplateId` IN (")
    val _inputSize: Int = _map.size()
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (i in 0 until _map.size()) {
      val _item: Long = _map.keyAt(i)
      _stmt.bindLong(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "workoutTemplateId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfWorkoutTemplateId: Int = 1
      val _columnIndexOfExerciseId: Int = 2
      val _columnIndexOfType: Int = 3
      val _columnIndexOfPosition: Int = 4
      val _collectionExercise: LongSparseArray<ExerciseEntity?> = LongSparseArray<ExerciseEntity?>()
      val _collectionPlannedSets: LongSparseArray<MutableList<PlannedSetEntity>> = LongSparseArray<MutableList<PlannedSetEntity>>()
      while (_stmt.step()) {
        val _tmpKey: Long
        _tmpKey = _stmt.getLong(_columnIndexOfExerciseId)
        _collectionExercise.put(_tmpKey, null)
        val _tmpKey_1: Long
        _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
        if (!_collectionPlannedSets.containsKey(_tmpKey_1)) {
          _collectionPlannedSets.put(_tmpKey_1, mutableListOf())
        }
      }
      _stmt.reset()
      __fetchRelationshipexerciseAscomGriffgymInfrastructureDatabaseEntityExerciseEntity(_connection, _collectionExercise)
      __fetchRelationshipplannedSetAscomGriffgymInfrastructureDatabaseEntityPlannedSetEntity(_connection, _collectionPlannedSets)
      while (_stmt.step()) {
        val _tmpKey_2: Long
        _tmpKey_2 = _stmt.getLong(_itemKeyIndex)
        val _tmpRelation: MutableList<ExerciseTemplateWithDetails>? = _map.get(_tmpKey_2)
        if (_tmpRelation != null) {
          val _item_1: ExerciseTemplateWithDetails
          val _tmpTemplate: ExerciseTemplateEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpWorkoutTemplateId: Long
          _tmpWorkoutTemplateId = _stmt.getLong(_columnIndexOfWorkoutTemplateId)
          val _tmpExerciseId: Long
          _tmpExerciseId = _stmt.getLong(_columnIndexOfExerciseId)
          val _tmpType: ExerciseType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfType)
          _tmpType = __griffGymConverters.stringToExerciseType(_tmp)
          val _tmpPosition: Int
          _tmpPosition = _stmt.getLong(_columnIndexOfPosition).toInt()
          _tmpTemplate = ExerciseTemplateEntity(_tmpId,_tmpWorkoutTemplateId,_tmpExerciseId,_tmpType,_tmpPosition)
          val _tmpExercise: ExerciseEntity?
          val _tmpKey_3: Long
          _tmpKey_3 = _stmt.getLong(_columnIndexOfExerciseId)
          _tmpExercise = _collectionExercise.get(_tmpKey_3)
          if (_tmpExercise == null) {
            error("Relationship item 'exercise' was expected to be NON-NULL but is NULL in @Relation involving a parent column named 'exerciseId' and entityColumn named 'id'.")
          }
          val _tmpPlannedSetsCollection: MutableList<PlannedSetEntity>
          val _tmpKey_4: Long
          _tmpKey_4 = _stmt.getLong(_columnIndexOfId)
          _tmpPlannedSetsCollection = checkNotNull(_collectionPlannedSets.get(_tmpKey_4))
          _item_1 = ExerciseTemplateWithDetails(_tmpTemplate,_tmpExercise,_tmpPlannedSetsCollection)
          _tmpRelation.add(_item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  private fun __fetchRelationshipworkoutTemplateAscomGriffgymInfrastructureDatabaseRelationWorkoutTemplateWithExercises(_connection: SQLiteConnection, _map: LongSparseArray<MutableList<WorkoutTemplateWithExercises>>) {
    if (_map.isEmpty()) {
      return
    }
    if (_map.size() > 999) {
      recursiveFetchLongSparseArray(_map, true) { _tmpMap ->
        __fetchRelationshipworkoutTemplateAscomGriffgymInfrastructureDatabaseRelationWorkoutTemplateWithExercises(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`weekId`,`dayNumber`,`sequenceNumber`,`title` FROM `workout_template` WHERE `weekId` IN (")
    val _inputSize: Int = _map.size()
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (i in 0 until _map.size()) {
      val _item: Long = _map.keyAt(i)
      _stmt.bindLong(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "weekId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfWeekId: Int = 1
      val _columnIndexOfDayNumber: Int = 2
      val _columnIndexOfSequenceNumber: Int = 3
      val _columnIndexOfTitle: Int = 4
      val _collectionExercises: LongSparseArray<MutableList<ExerciseTemplateWithDetails>> = LongSparseArray<MutableList<ExerciseTemplateWithDetails>>()
      while (_stmt.step()) {
        val _tmpKey: Long
        _tmpKey = _stmt.getLong(_columnIndexOfId)
        if (!_collectionExercises.containsKey(_tmpKey)) {
          _collectionExercises.put(_tmpKey, mutableListOf())
        }
      }
      _stmt.reset()
      __fetchRelationshipexerciseTemplateAscomGriffgymInfrastructureDatabaseRelationExerciseTemplateWithDetails(_connection, _collectionExercises)
      while (_stmt.step()) {
        val _tmpKey_1: Long
        _tmpKey_1 = _stmt.getLong(_itemKeyIndex)
        val _tmpRelation: MutableList<WorkoutTemplateWithExercises>? = _map.get(_tmpKey_1)
        if (_tmpRelation != null) {
          val _item_1: WorkoutTemplateWithExercises
          val _tmpTemplate: WorkoutTemplateEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpWeekId: Long
          _tmpWeekId = _stmt.getLong(_columnIndexOfWeekId)
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpSequenceNumber: Int
          _tmpSequenceNumber = _stmt.getLong(_columnIndexOfSequenceNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          _tmpTemplate = WorkoutTemplateEntity(_tmpId,_tmpWeekId,_tmpDayNumber,_tmpSequenceNumber,_tmpTitle)
          val _tmpExercisesCollection: MutableList<ExerciseTemplateWithDetails>
          val _tmpKey_2: Long
          _tmpKey_2 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_2))
          _item_1 = WorkoutTemplateWithExercises(_tmpTemplate,_tmpExercisesCollection)
          _tmpRelation.add(_item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  private fun __fetchRelationshiptrainingWeekAscomGriffgymInfrastructureDatabaseRelationTrainingWeekWithWorkouts(_connection: SQLiteConnection, _map: LongSparseArray<MutableList<TrainingWeekWithWorkouts>>) {
    if (_map.isEmpty()) {
      return
    }
    if (_map.size() > 999) {
      recursiveFetchLongSparseArray(_map, true) { _tmpMap ->
        __fetchRelationshiptrainingWeekAscomGriffgymInfrastructureDatabaseRelationTrainingWeekWithWorkouts(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`programId`,`weekNumber`,`label`,`isDeload` FROM `training_week` WHERE `programId` IN (")
    val _inputSize: Int = _map.size()
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (i in 0 until _map.size()) {
      val _item: Long = _map.keyAt(i)
      _stmt.bindLong(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "programId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfProgramId: Int = 1
      val _columnIndexOfWeekNumber: Int = 2
      val _columnIndexOfLabel: Int = 3
      val _columnIndexOfIsDeload: Int = 4
      val _collectionWorkouts: LongSparseArray<MutableList<WorkoutTemplateWithExercises>> = LongSparseArray<MutableList<WorkoutTemplateWithExercises>>()
      while (_stmt.step()) {
        val _tmpKey: Long
        _tmpKey = _stmt.getLong(_columnIndexOfId)
        if (!_collectionWorkouts.containsKey(_tmpKey)) {
          _collectionWorkouts.put(_tmpKey, mutableListOf())
        }
      }
      _stmt.reset()
      __fetchRelationshipworkoutTemplateAscomGriffgymInfrastructureDatabaseRelationWorkoutTemplateWithExercises(_connection, _collectionWorkouts)
      while (_stmt.step()) {
        val _tmpKey_1: Long
        _tmpKey_1 = _stmt.getLong(_itemKeyIndex)
        val _tmpRelation: MutableList<TrainingWeekWithWorkouts>? = _map.get(_tmpKey_1)
        if (_tmpRelation != null) {
          val _item_1: TrainingWeekWithWorkouts
          val _tmpWeek: TrainingWeekEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProgramId: Long
          _tmpProgramId = _stmt.getLong(_columnIndexOfProgramId)
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpLabel: String
          _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          val _tmpIsDeload: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp != 0
          _tmpWeek = TrainingWeekEntity(_tmpId,_tmpProgramId,_tmpWeekNumber,_tmpLabel,_tmpIsDeload)
          val _tmpWorkoutsCollection: MutableList<WorkoutTemplateWithExercises>
          val _tmpKey_2: Long
          _tmpKey_2 = _stmt.getLong(_columnIndexOfId)
          _tmpWorkoutsCollection = checkNotNull(_collectionWorkouts.get(_tmpKey_2))
          _item_1 = TrainingWeekWithWorkouts(_tmpWeek,_tmpWorkoutsCollection)
          _tmpRelation.add(_item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
