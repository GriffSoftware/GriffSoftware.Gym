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
import com.griffgym.domain.model.WorkoutStatus
import com.griffgym.infrastructure.database.converter.GriffGymConverters
import com.griffgym.infrastructure.database.entity.ExerciseEntity
import com.griffgym.infrastructure.database.entity.ExerciseLogEntity
import com.griffgym.infrastructure.database.entity.SetLogEntity
import com.griffgym.infrastructure.database.entity.WorkoutSessionEntity
import com.griffgym.infrastructure.database.relation.ExerciseLogWithDetails
import com.griffgym.infrastructure.database.relation.WorkoutSessionWithExercises
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
public class WorkoutSessionDao_Impl(
  __db: RoomDatabase,
) : WorkoutSessionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfWorkoutSessionEntity: EntityInsertAdapter<WorkoutSessionEntity>

  private val __griffGymConverters: GriffGymConverters = GriffGymConverters()

  private val __insertAdapterOfExerciseLogEntity: EntityInsertAdapter<ExerciseLogEntity>

  private val __insertAdapterOfSetLogEntity: EntityInsertAdapter<SetLogEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfWorkoutSessionEntity = object : EntityInsertAdapter<WorkoutSessionEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `workout_session` (`id`,`templateId`,`weekNumber`,`dayNumber`,`title`,`isDeload`,`status`,`date`,`startedAt`,`finishedAt`,`totalVolumeKg`,`notes`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WorkoutSessionEntity) {
        statement.bindLong(1, entity.id)
        val _tmpTemplateId: Long? = entity.templateId
        if (_tmpTemplateId == null) {
          statement.bindNull(2)
        } else {
          statement.bindLong(2, _tmpTemplateId)
        }
        statement.bindLong(3, entity.weekNumber.toLong())
        statement.bindLong(4, entity.dayNumber.toLong())
        statement.bindText(5, entity.title)
        val _tmp: Int = if (entity.isDeload) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        val _tmp_1: String = __griffGymConverters.workoutStatusToString(entity.status)
        statement.bindText(7, _tmp_1)
        statement.bindLong(8, entity.date)
        val _tmp_2: Long? = __griffGymConverters.instantToEpochMillis(entity.startedAt)
        if (_tmp_2 == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmp_2)
        }
        val _tmpFinishedAt: Instant? = entity.finishedAt
        val _tmp_3: Long? = __griffGymConverters.instantToEpochMillis(_tmpFinishedAt)
        if (_tmp_3 == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmp_3)
        }
        val _tmpTotalVolumeKg: Double? = entity.totalVolumeKg
        if (_tmpTotalVolumeKg == null) {
          statement.bindNull(11)
        } else {
          statement.bindDouble(11, _tmpTotalVolumeKg)
        }
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpNotes)
        }
      }
    }
    this.__insertAdapterOfExerciseLogEntity = object : EntityInsertAdapter<ExerciseLogEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `exercise_log` (`id`,`sessionId`,`exerciseId`,`type`,`position`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ExerciseLogEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.sessionId)
        statement.bindLong(3, entity.exerciseId)
        val _tmp: String = __griffGymConverters.exerciseTypeToString(entity.type)
        statement.bindText(4, _tmp)
        statement.bindLong(5, entity.position.toLong())
      }
    }
    this.__insertAdapterOfSetLogEntity = object : EntityInsertAdapter<SetLogEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `set_log` (`id`,`exerciseLogId`,`position`,`plannedWeightKg`,`plannedReps`,`plannedRpeMin`,`plannedRpeMax`,`actualWeightKg`,`actualReps`,`actualRpe`,`completed`,`notes`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SetLogEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.exerciseLogId)
        statement.bindLong(3, entity.position.toLong())
        val _tmpPlannedWeightKg: Double? = entity.plannedWeightKg
        if (_tmpPlannedWeightKg == null) {
          statement.bindNull(4)
        } else {
          statement.bindDouble(4, _tmpPlannedWeightKg)
        }
        val _tmpPlannedReps: Int? = entity.plannedReps
        if (_tmpPlannedReps == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpPlannedReps.toLong())
        }
        val _tmpPlannedRpeMin: Double? = entity.plannedRpeMin
        if (_tmpPlannedRpeMin == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpPlannedRpeMin)
        }
        val _tmpPlannedRpeMax: Double? = entity.plannedRpeMax
        if (_tmpPlannedRpeMax == null) {
          statement.bindNull(7)
        } else {
          statement.bindDouble(7, _tmpPlannedRpeMax)
        }
        val _tmpActualWeightKg: Double? = entity.actualWeightKg
        if (_tmpActualWeightKg == null) {
          statement.bindNull(8)
        } else {
          statement.bindDouble(8, _tmpActualWeightKg)
        }
        val _tmpActualReps: Int? = entity.actualReps
        if (_tmpActualReps == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpActualReps.toLong())
        }
        val _tmpActualRpe: Double? = entity.actualRpe
        if (_tmpActualRpe == null) {
          statement.bindNull(10)
        } else {
          statement.bindDouble(10, _tmpActualRpe)
        }
        val _tmp: Int = if (entity.completed) 1 else 0
        statement.bindLong(11, _tmp.toLong())
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpNotes)
        }
      }
    }
  }

  public override suspend fun insertSession(session: WorkoutSessionEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfWorkoutSessionEntity.insertAndReturnId(_connection, session)
    _result
  }

  public override suspend fun insertExerciseLog(log: ExerciseLogEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfExerciseLogEntity.insertAndReturnId(_connection, log)
    _result
  }

  public override suspend fun insertSetLogs(sets: List<SetLogEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfSetLogEntity.insert(_connection, sets)
  }

  public override suspend fun insertSetLog(`set`: SetLogEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfSetLogEntity.insertAndReturnId(_connection, set)
    _result
  }

  public override fun observeByStatus(status: WorkoutStatus): Flow<WorkoutSessionWithExercises?> {
    val _sql: String = "SELECT * FROM workout_session WHERE status = ? ORDER BY startedAt DESC LIMIT 1"
    return createFlow(__db, true, arrayOf("exercise", "set_log", "exercise_log", "workout_session")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: String = __griffGymConverters.workoutStatusToString(status)
        _stmt.bindText(_argIndex, _tmp)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTemplateId: Int = getColumnIndexOrThrow(_stmt, "templateId")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfDayNumber: Int = getColumnIndexOrThrow(_stmt, "dayNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "startedAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfTotalVolumeKg: Int = getColumnIndexOrThrow(_stmt, "totalVolumeKg")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _collectionExercises: LongSparseArray<MutableList<ExerciseLogWithDetails>> = LongSparseArray<MutableList<ExerciseLogWithDetails>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionExercises.containsKey(_tmpKey)) {
            _collectionExercises.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipexerciseLogAscomGriffgymInfrastructureDatabaseRelationExerciseLogWithDetails(_connection, _collectionExercises)
        val _result: WorkoutSessionWithExercises?
        if (_stmt.step()) {
          val _tmpSession: WorkoutSessionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTemplateId: Long?
          if (_stmt.isNull(_columnIndexOfTemplateId)) {
            _tmpTemplateId = null
          } else {
            _tmpTemplateId = _stmt.getLong(_columnIndexOfTemplateId)
          }
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpIsDeload: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp_1 != 0
          val _tmpStatus: WorkoutStatus
          val _tmp_2: String
          _tmp_2 = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __griffGymConverters.stringToWorkoutStatus(_tmp_2)
          val _tmpDate: Long
          _tmpDate = _stmt.getLong(_columnIndexOfDate)
          val _tmpStartedAt: Instant
          val _tmp_3: Long?
          if (_stmt.isNull(_columnIndexOfStartedAt)) {
            _tmp_3 = null
          } else {
            _tmp_3 = _stmt.getLong(_columnIndexOfStartedAt)
          }
          val _tmp_4: Instant? = __griffGymConverters.epochMillisToInstant(_tmp_3)
          if (_tmp_4 == null) {
            error("Expected NON-NULL 'java.time.Instant', but it was NULL.")
          } else {
            _tmpStartedAt = _tmp_4
          }
          val _tmpFinishedAt: Instant?
          val _tmp_5: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmp_5 = null
          } else {
            _tmp_5 = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          _tmpFinishedAt = __griffGymConverters.epochMillisToInstant(_tmp_5)
          val _tmpTotalVolumeKg: Double?
          if (_stmt.isNull(_columnIndexOfTotalVolumeKg)) {
            _tmpTotalVolumeKg = null
          } else {
            _tmpTotalVolumeKg = _stmt.getDouble(_columnIndexOfTotalVolumeKg)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _tmpSession = WorkoutSessionEntity(_tmpId,_tmpTemplateId,_tmpWeekNumber,_tmpDayNumber,_tmpTitle,_tmpIsDeload,_tmpStatus,_tmpDate,_tmpStartedAt,_tmpFinishedAt,_tmpTotalVolumeKg,_tmpNotes)
          val _tmpExercisesCollection: MutableList<ExerciseLogWithDetails>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_1))
          _result = WorkoutSessionWithExercises(_tmpSession,_tmpExercisesCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByStatus(status: WorkoutStatus): WorkoutSessionWithExercises? {
    val _sql: String = "SELECT * FROM workout_session WHERE status = ? ORDER BY startedAt DESC LIMIT 1"
    return performSuspending(__db, true, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: String = __griffGymConverters.workoutStatusToString(status)
        _stmt.bindText(_argIndex, _tmp)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTemplateId: Int = getColumnIndexOrThrow(_stmt, "templateId")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfDayNumber: Int = getColumnIndexOrThrow(_stmt, "dayNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "startedAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfTotalVolumeKg: Int = getColumnIndexOrThrow(_stmt, "totalVolumeKg")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _collectionExercises: LongSparseArray<MutableList<ExerciseLogWithDetails>> = LongSparseArray<MutableList<ExerciseLogWithDetails>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionExercises.containsKey(_tmpKey)) {
            _collectionExercises.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipexerciseLogAscomGriffgymInfrastructureDatabaseRelationExerciseLogWithDetails(_connection, _collectionExercises)
        val _result: WorkoutSessionWithExercises?
        if (_stmt.step()) {
          val _tmpSession: WorkoutSessionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTemplateId: Long?
          if (_stmt.isNull(_columnIndexOfTemplateId)) {
            _tmpTemplateId = null
          } else {
            _tmpTemplateId = _stmt.getLong(_columnIndexOfTemplateId)
          }
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpIsDeload: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp_1 != 0
          val _tmpStatus: WorkoutStatus
          val _tmp_2: String
          _tmp_2 = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __griffGymConverters.stringToWorkoutStatus(_tmp_2)
          val _tmpDate: Long
          _tmpDate = _stmt.getLong(_columnIndexOfDate)
          val _tmpStartedAt: Instant
          val _tmp_3: Long?
          if (_stmt.isNull(_columnIndexOfStartedAt)) {
            _tmp_3 = null
          } else {
            _tmp_3 = _stmt.getLong(_columnIndexOfStartedAt)
          }
          val _tmp_4: Instant? = __griffGymConverters.epochMillisToInstant(_tmp_3)
          if (_tmp_4 == null) {
            error("Expected NON-NULL 'java.time.Instant', but it was NULL.")
          } else {
            _tmpStartedAt = _tmp_4
          }
          val _tmpFinishedAt: Instant?
          val _tmp_5: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmp_5 = null
          } else {
            _tmp_5 = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          _tmpFinishedAt = __griffGymConverters.epochMillisToInstant(_tmp_5)
          val _tmpTotalVolumeKg: Double?
          if (_stmt.isNull(_columnIndexOfTotalVolumeKg)) {
            _tmpTotalVolumeKg = null
          } else {
            _tmpTotalVolumeKg = _stmt.getDouble(_columnIndexOfTotalVolumeKg)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _tmpSession = WorkoutSessionEntity(_tmpId,_tmpTemplateId,_tmpWeekNumber,_tmpDayNumber,_tmpTitle,_tmpIsDeload,_tmpStatus,_tmpDate,_tmpStartedAt,_tmpFinishedAt,_tmpTotalVolumeKg,_tmpNotes)
          val _tmpExercisesCollection: MutableList<ExerciseLogWithDetails>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_1))
          _result = WorkoutSessionWithExercises(_tmpSession,_tmpExercisesCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeById(id: Long): Flow<WorkoutSessionWithExercises?> {
    val _sql: String = "SELECT * FROM workout_session WHERE id = ?"
    return createFlow(__db, true, arrayOf("exercise", "set_log", "exercise_log", "workout_session")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTemplateId: Int = getColumnIndexOrThrow(_stmt, "templateId")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfDayNumber: Int = getColumnIndexOrThrow(_stmt, "dayNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "startedAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfTotalVolumeKg: Int = getColumnIndexOrThrow(_stmt, "totalVolumeKg")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _collectionExercises: LongSparseArray<MutableList<ExerciseLogWithDetails>> = LongSparseArray<MutableList<ExerciseLogWithDetails>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionExercises.containsKey(_tmpKey)) {
            _collectionExercises.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipexerciseLogAscomGriffgymInfrastructureDatabaseRelationExerciseLogWithDetails(_connection, _collectionExercises)
        val _result: WorkoutSessionWithExercises?
        if (_stmt.step()) {
          val _tmpSession: WorkoutSessionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTemplateId: Long?
          if (_stmt.isNull(_columnIndexOfTemplateId)) {
            _tmpTemplateId = null
          } else {
            _tmpTemplateId = _stmt.getLong(_columnIndexOfTemplateId)
          }
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpIsDeload: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp != 0
          val _tmpStatus: WorkoutStatus
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __griffGymConverters.stringToWorkoutStatus(_tmp_1)
          val _tmpDate: Long
          _tmpDate = _stmt.getLong(_columnIndexOfDate)
          val _tmpStartedAt: Instant
          val _tmp_2: Long?
          if (_stmt.isNull(_columnIndexOfStartedAt)) {
            _tmp_2 = null
          } else {
            _tmp_2 = _stmt.getLong(_columnIndexOfStartedAt)
          }
          val _tmp_3: Instant? = __griffGymConverters.epochMillisToInstant(_tmp_2)
          if (_tmp_3 == null) {
            error("Expected NON-NULL 'java.time.Instant', but it was NULL.")
          } else {
            _tmpStartedAt = _tmp_3
          }
          val _tmpFinishedAt: Instant?
          val _tmp_4: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmp_4 = null
          } else {
            _tmp_4 = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          _tmpFinishedAt = __griffGymConverters.epochMillisToInstant(_tmp_4)
          val _tmpTotalVolumeKg: Double?
          if (_stmt.isNull(_columnIndexOfTotalVolumeKg)) {
            _tmpTotalVolumeKg = null
          } else {
            _tmpTotalVolumeKg = _stmt.getDouble(_columnIndexOfTotalVolumeKg)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _tmpSession = WorkoutSessionEntity(_tmpId,_tmpTemplateId,_tmpWeekNumber,_tmpDayNumber,_tmpTitle,_tmpIsDeload,_tmpStatus,_tmpDate,_tmpStartedAt,_tmpFinishedAt,_tmpTotalVolumeKg,_tmpNotes)
          val _tmpExercisesCollection: MutableList<ExerciseLogWithDetails>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_1))
          _result = WorkoutSessionWithExercises(_tmpSession,_tmpExercisesCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): WorkoutSessionWithExercises? {
    val _sql: String = "SELECT * FROM workout_session WHERE id = ?"
    return performSuspending(__db, true, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTemplateId: Int = getColumnIndexOrThrow(_stmt, "templateId")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfDayNumber: Int = getColumnIndexOrThrow(_stmt, "dayNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "startedAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfTotalVolumeKg: Int = getColumnIndexOrThrow(_stmt, "totalVolumeKg")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _collectionExercises: LongSparseArray<MutableList<ExerciseLogWithDetails>> = LongSparseArray<MutableList<ExerciseLogWithDetails>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionExercises.containsKey(_tmpKey)) {
            _collectionExercises.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipexerciseLogAscomGriffgymInfrastructureDatabaseRelationExerciseLogWithDetails(_connection, _collectionExercises)
        val _result: WorkoutSessionWithExercises?
        if (_stmt.step()) {
          val _tmpSession: WorkoutSessionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTemplateId: Long?
          if (_stmt.isNull(_columnIndexOfTemplateId)) {
            _tmpTemplateId = null
          } else {
            _tmpTemplateId = _stmt.getLong(_columnIndexOfTemplateId)
          }
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpIsDeload: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp != 0
          val _tmpStatus: WorkoutStatus
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __griffGymConverters.stringToWorkoutStatus(_tmp_1)
          val _tmpDate: Long
          _tmpDate = _stmt.getLong(_columnIndexOfDate)
          val _tmpStartedAt: Instant
          val _tmp_2: Long?
          if (_stmt.isNull(_columnIndexOfStartedAt)) {
            _tmp_2 = null
          } else {
            _tmp_2 = _stmt.getLong(_columnIndexOfStartedAt)
          }
          val _tmp_3: Instant? = __griffGymConverters.epochMillisToInstant(_tmp_2)
          if (_tmp_3 == null) {
            error("Expected NON-NULL 'java.time.Instant', but it was NULL.")
          } else {
            _tmpStartedAt = _tmp_3
          }
          val _tmpFinishedAt: Instant?
          val _tmp_4: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmp_4 = null
          } else {
            _tmp_4 = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          _tmpFinishedAt = __griffGymConverters.epochMillisToInstant(_tmp_4)
          val _tmpTotalVolumeKg: Double?
          if (_stmt.isNull(_columnIndexOfTotalVolumeKg)) {
            _tmpTotalVolumeKg = null
          } else {
            _tmpTotalVolumeKg = _stmt.getDouble(_columnIndexOfTotalVolumeKg)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _tmpSession = WorkoutSessionEntity(_tmpId,_tmpTemplateId,_tmpWeekNumber,_tmpDayNumber,_tmpTitle,_tmpIsDeload,_tmpStatus,_tmpDate,_tmpStartedAt,_tmpFinishedAt,_tmpTotalVolumeKg,_tmpNotes)
          val _tmpExercisesCollection: MutableList<ExerciseLogWithDetails>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_1))
          _result = WorkoutSessionWithExercises(_tmpSession,_tmpExercisesCollection)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeHistory(): Flow<List<WorkoutSessionWithExercises>> {
    val _sql: String = "SELECT * FROM workout_session WHERE status != 'IN_PROGRESS' ORDER BY date DESC, startedAt DESC"
    return createFlow(__db, true, arrayOf("exercise", "set_log", "exercise_log", "workout_session")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTemplateId: Int = getColumnIndexOrThrow(_stmt, "templateId")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfDayNumber: Int = getColumnIndexOrThrow(_stmt, "dayNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "startedAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfTotalVolumeKg: Int = getColumnIndexOrThrow(_stmt, "totalVolumeKg")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _collectionExercises: LongSparseArray<MutableList<ExerciseLogWithDetails>> = LongSparseArray<MutableList<ExerciseLogWithDetails>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionExercises.containsKey(_tmpKey)) {
            _collectionExercises.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipexerciseLogAscomGriffgymInfrastructureDatabaseRelationExerciseLogWithDetails(_connection, _collectionExercises)
        val _result: MutableList<WorkoutSessionWithExercises> = mutableListOf()
        while (_stmt.step()) {
          val _item: WorkoutSessionWithExercises
          val _tmpSession: WorkoutSessionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTemplateId: Long?
          if (_stmt.isNull(_columnIndexOfTemplateId)) {
            _tmpTemplateId = null
          } else {
            _tmpTemplateId = _stmt.getLong(_columnIndexOfTemplateId)
          }
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpIsDeload: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp != 0
          val _tmpStatus: WorkoutStatus
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __griffGymConverters.stringToWorkoutStatus(_tmp_1)
          val _tmpDate: Long
          _tmpDate = _stmt.getLong(_columnIndexOfDate)
          val _tmpStartedAt: Instant
          val _tmp_2: Long?
          if (_stmt.isNull(_columnIndexOfStartedAt)) {
            _tmp_2 = null
          } else {
            _tmp_2 = _stmt.getLong(_columnIndexOfStartedAt)
          }
          val _tmp_3: Instant? = __griffGymConverters.epochMillisToInstant(_tmp_2)
          if (_tmp_3 == null) {
            error("Expected NON-NULL 'java.time.Instant', but it was NULL.")
          } else {
            _tmpStartedAt = _tmp_3
          }
          val _tmpFinishedAt: Instant?
          val _tmp_4: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmp_4 = null
          } else {
            _tmp_4 = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          _tmpFinishedAt = __griffGymConverters.epochMillisToInstant(_tmp_4)
          val _tmpTotalVolumeKg: Double?
          if (_stmt.isNull(_columnIndexOfTotalVolumeKg)) {
            _tmpTotalVolumeKg = null
          } else {
            _tmpTotalVolumeKg = _stmt.getDouble(_columnIndexOfTotalVolumeKg)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _tmpSession = WorkoutSessionEntity(_tmpId,_tmpTemplateId,_tmpWeekNumber,_tmpDayNumber,_tmpTitle,_tmpIsDeload,_tmpStatus,_tmpDate,_tmpStartedAt,_tmpFinishedAt,_tmpTotalVolumeKg,_tmpNotes)
          val _tmpExercisesCollection: MutableList<ExerciseLogWithDetails>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_1))
          _item = WorkoutSessionWithExercises(_tmpSession,_tmpExercisesCollection)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeCompleted(): Flow<List<WorkoutSessionWithExercises>> {
    val _sql: String = "SELECT * FROM workout_session WHERE status = 'COMPLETED' ORDER BY date ASC, startedAt ASC"
    return createFlow(__db, true, arrayOf("exercise", "set_log", "exercise_log", "workout_session")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTemplateId: Int = getColumnIndexOrThrow(_stmt, "templateId")
        val _columnIndexOfWeekNumber: Int = getColumnIndexOrThrow(_stmt, "weekNumber")
        val _columnIndexOfDayNumber: Int = getColumnIndexOrThrow(_stmt, "dayNumber")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfIsDeload: Int = getColumnIndexOrThrow(_stmt, "isDeload")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "startedAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfTotalVolumeKg: Int = getColumnIndexOrThrow(_stmt, "totalVolumeKg")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _collectionExercises: LongSparseArray<MutableList<ExerciseLogWithDetails>> = LongSparseArray<MutableList<ExerciseLogWithDetails>>()
        while (_stmt.step()) {
          val _tmpKey: Long
          _tmpKey = _stmt.getLong(_columnIndexOfId)
          if (!_collectionExercises.containsKey(_tmpKey)) {
            _collectionExercises.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipexerciseLogAscomGriffgymInfrastructureDatabaseRelationExerciseLogWithDetails(_connection, _collectionExercises)
        val _result: MutableList<WorkoutSessionWithExercises> = mutableListOf()
        while (_stmt.step()) {
          val _item: WorkoutSessionWithExercises
          val _tmpSession: WorkoutSessionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTemplateId: Long?
          if (_stmt.isNull(_columnIndexOfTemplateId)) {
            _tmpTemplateId = null
          } else {
            _tmpTemplateId = _stmt.getLong(_columnIndexOfTemplateId)
          }
          val _tmpWeekNumber: Int
          _tmpWeekNumber = _stmt.getLong(_columnIndexOfWeekNumber).toInt()
          val _tmpDayNumber: Int
          _tmpDayNumber = _stmt.getLong(_columnIndexOfDayNumber).toInt()
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpIsDeload: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsDeload).toInt()
          _tmpIsDeload = _tmp != 0
          val _tmpStatus: WorkoutStatus
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __griffGymConverters.stringToWorkoutStatus(_tmp_1)
          val _tmpDate: Long
          _tmpDate = _stmt.getLong(_columnIndexOfDate)
          val _tmpStartedAt: Instant
          val _tmp_2: Long?
          if (_stmt.isNull(_columnIndexOfStartedAt)) {
            _tmp_2 = null
          } else {
            _tmp_2 = _stmt.getLong(_columnIndexOfStartedAt)
          }
          val _tmp_3: Instant? = __griffGymConverters.epochMillisToInstant(_tmp_2)
          if (_tmp_3 == null) {
            error("Expected NON-NULL 'java.time.Instant', but it was NULL.")
          } else {
            _tmpStartedAt = _tmp_3
          }
          val _tmpFinishedAt: Instant?
          val _tmp_4: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmp_4 = null
          } else {
            _tmp_4 = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          _tmpFinishedAt = __griffGymConverters.epochMillisToInstant(_tmp_4)
          val _tmpTotalVolumeKg: Double?
          if (_stmt.isNull(_columnIndexOfTotalVolumeKg)) {
            _tmpTotalVolumeKg = null
          } else {
            _tmpTotalVolumeKg = _stmt.getDouble(_columnIndexOfTotalVolumeKg)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _tmpSession = WorkoutSessionEntity(_tmpId,_tmpTemplateId,_tmpWeekNumber,_tmpDayNumber,_tmpTitle,_tmpIsDeload,_tmpStatus,_tmpDate,_tmpStartedAt,_tmpFinishedAt,_tmpTotalVolumeKg,_tmpNotes)
          val _tmpExercisesCollection: MutableList<ExerciseLogWithDetails>
          val _tmpKey_1: Long
          _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
          _tmpExercisesCollection = checkNotNull(_collectionExercises.get(_tmpKey_1))
          _item = WorkoutSessionWithExercises(_tmpSession,_tmpExercisesCollection)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getSetLog(id: Long): SetLogEntity? {
    val _sql: String = "SELECT * FROM set_log WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfExerciseLogId: Int = getColumnIndexOrThrow(_stmt, "exerciseLogId")
        val _columnIndexOfPosition: Int = getColumnIndexOrThrow(_stmt, "position")
        val _columnIndexOfPlannedWeightKg: Int = getColumnIndexOrThrow(_stmt, "plannedWeightKg")
        val _columnIndexOfPlannedReps: Int = getColumnIndexOrThrow(_stmt, "plannedReps")
        val _columnIndexOfPlannedRpeMin: Int = getColumnIndexOrThrow(_stmt, "plannedRpeMin")
        val _columnIndexOfPlannedRpeMax: Int = getColumnIndexOrThrow(_stmt, "plannedRpeMax")
        val _columnIndexOfActualWeightKg: Int = getColumnIndexOrThrow(_stmt, "actualWeightKg")
        val _columnIndexOfActualReps: Int = getColumnIndexOrThrow(_stmt, "actualReps")
        val _columnIndexOfActualRpe: Int = getColumnIndexOrThrow(_stmt, "actualRpe")
        val _columnIndexOfCompleted: Int = getColumnIndexOrThrow(_stmt, "completed")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _result: SetLogEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpExerciseLogId: Long
          _tmpExerciseLogId = _stmt.getLong(_columnIndexOfExerciseLogId)
          val _tmpPosition: Int
          _tmpPosition = _stmt.getLong(_columnIndexOfPosition).toInt()
          val _tmpPlannedWeightKg: Double?
          if (_stmt.isNull(_columnIndexOfPlannedWeightKg)) {
            _tmpPlannedWeightKg = null
          } else {
            _tmpPlannedWeightKg = _stmt.getDouble(_columnIndexOfPlannedWeightKg)
          }
          val _tmpPlannedReps: Int?
          if (_stmt.isNull(_columnIndexOfPlannedReps)) {
            _tmpPlannedReps = null
          } else {
            _tmpPlannedReps = _stmt.getLong(_columnIndexOfPlannedReps).toInt()
          }
          val _tmpPlannedRpeMin: Double?
          if (_stmt.isNull(_columnIndexOfPlannedRpeMin)) {
            _tmpPlannedRpeMin = null
          } else {
            _tmpPlannedRpeMin = _stmt.getDouble(_columnIndexOfPlannedRpeMin)
          }
          val _tmpPlannedRpeMax: Double?
          if (_stmt.isNull(_columnIndexOfPlannedRpeMax)) {
            _tmpPlannedRpeMax = null
          } else {
            _tmpPlannedRpeMax = _stmt.getDouble(_columnIndexOfPlannedRpeMax)
          }
          val _tmpActualWeightKg: Double?
          if (_stmt.isNull(_columnIndexOfActualWeightKg)) {
            _tmpActualWeightKg = null
          } else {
            _tmpActualWeightKg = _stmt.getDouble(_columnIndexOfActualWeightKg)
          }
          val _tmpActualReps: Int?
          if (_stmt.isNull(_columnIndexOfActualReps)) {
            _tmpActualReps = null
          } else {
            _tmpActualReps = _stmt.getLong(_columnIndexOfActualReps).toInt()
          }
          val _tmpActualRpe: Double?
          if (_stmt.isNull(_columnIndexOfActualRpe)) {
            _tmpActualRpe = null
          } else {
            _tmpActualRpe = _stmt.getDouble(_columnIndexOfActualRpe)
          }
          val _tmpCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfCompleted).toInt()
          _tmpCompleted = _tmp != 0
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _result = SetLogEntity(_tmpId,_tmpExerciseLogId,_tmpPosition,_tmpPlannedWeightKg,_tmpPlannedReps,_tmpPlannedRpeMin,_tmpPlannedRpeMax,_tmpActualWeightKg,_tmpActualReps,_tmpActualRpe,_tmpCompleted,_tmpNotes)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun lastSetOf(exerciseLogId: Long): SetLogEntity? {
    val _sql: String = "SELECT * FROM set_log WHERE exerciseLogId = ? ORDER BY position DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, exerciseLogId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfExerciseLogId: Int = getColumnIndexOrThrow(_stmt, "exerciseLogId")
        val _columnIndexOfPosition: Int = getColumnIndexOrThrow(_stmt, "position")
        val _columnIndexOfPlannedWeightKg: Int = getColumnIndexOrThrow(_stmt, "plannedWeightKg")
        val _columnIndexOfPlannedReps: Int = getColumnIndexOrThrow(_stmt, "plannedReps")
        val _columnIndexOfPlannedRpeMin: Int = getColumnIndexOrThrow(_stmt, "plannedRpeMin")
        val _columnIndexOfPlannedRpeMax: Int = getColumnIndexOrThrow(_stmt, "plannedRpeMax")
        val _columnIndexOfActualWeightKg: Int = getColumnIndexOrThrow(_stmt, "actualWeightKg")
        val _columnIndexOfActualReps: Int = getColumnIndexOrThrow(_stmt, "actualReps")
        val _columnIndexOfActualRpe: Int = getColumnIndexOrThrow(_stmt, "actualRpe")
        val _columnIndexOfCompleted: Int = getColumnIndexOrThrow(_stmt, "completed")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _result: SetLogEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpExerciseLogId: Long
          _tmpExerciseLogId = _stmt.getLong(_columnIndexOfExerciseLogId)
          val _tmpPosition: Int
          _tmpPosition = _stmt.getLong(_columnIndexOfPosition).toInt()
          val _tmpPlannedWeightKg: Double?
          if (_stmt.isNull(_columnIndexOfPlannedWeightKg)) {
            _tmpPlannedWeightKg = null
          } else {
            _tmpPlannedWeightKg = _stmt.getDouble(_columnIndexOfPlannedWeightKg)
          }
          val _tmpPlannedReps: Int?
          if (_stmt.isNull(_columnIndexOfPlannedReps)) {
            _tmpPlannedReps = null
          } else {
            _tmpPlannedReps = _stmt.getLong(_columnIndexOfPlannedReps).toInt()
          }
          val _tmpPlannedRpeMin: Double?
          if (_stmt.isNull(_columnIndexOfPlannedRpeMin)) {
            _tmpPlannedRpeMin = null
          } else {
            _tmpPlannedRpeMin = _stmt.getDouble(_columnIndexOfPlannedRpeMin)
          }
          val _tmpPlannedRpeMax: Double?
          if (_stmt.isNull(_columnIndexOfPlannedRpeMax)) {
            _tmpPlannedRpeMax = null
          } else {
            _tmpPlannedRpeMax = _stmt.getDouble(_columnIndexOfPlannedRpeMax)
          }
          val _tmpActualWeightKg: Double?
          if (_stmt.isNull(_columnIndexOfActualWeightKg)) {
            _tmpActualWeightKg = null
          } else {
            _tmpActualWeightKg = _stmt.getDouble(_columnIndexOfActualWeightKg)
          }
          val _tmpActualReps: Int?
          if (_stmt.isNull(_columnIndexOfActualReps)) {
            _tmpActualReps = null
          } else {
            _tmpActualReps = _stmt.getLong(_columnIndexOfActualReps).toInt()
          }
          val _tmpActualRpe: Double?
          if (_stmt.isNull(_columnIndexOfActualRpe)) {
            _tmpActualRpe = null
          } else {
            _tmpActualRpe = _stmt.getDouble(_columnIndexOfActualRpe)
          }
          val _tmpCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfCompleted).toInt()
          _tmpCompleted = _tmp != 0
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _result = SetLogEntity(_tmpId,_tmpExerciseLogId,_tmpPosition,_tmpPlannedWeightKg,_tmpPlannedReps,_tmpPlannedRpeMin,_tmpPlannedRpeMax,_tmpActualWeightKg,_tmpActualReps,_tmpActualRpe,_tmpCompleted,_tmpNotes)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun lastSetPosition(exerciseLogId: Long): Int {
    val _sql: String = "SELECT COALESCE(MAX(position), 0) FROM set_log WHERE exerciseLogId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, exerciseLogId)
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

  public override suspend fun lastExercisePosition(sessionId: Long): Int {
    val _sql: String = "SELECT COALESCE(MAX(position), 0) FROM exercise_log WHERE sessionId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
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

  public override suspend fun updateSetResult(
    id: Long,
    weightKg: Double?,
    reps: Int?,
    rpe: Double?,
    completed: Boolean,
    notes: String?,
  ) {
    val _sql: String = "UPDATE set_log SET actualWeightKg = ?, actualReps = ?, actualRpe = ?, completed = ?, notes = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        if (weightKg == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindDouble(_argIndex, weightKg)
        }
        _argIndex = 2
        if (reps == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindLong(_argIndex, reps.toLong())
        }
        _argIndex = 3
        if (rpe == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindDouble(_argIndex, rpe)
        }
        _argIndex = 4
        val _tmp: Int = if (completed) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 5
        if (notes == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, notes)
        }
        _argIndex = 6
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteSetLog(id: Long) {
    val _sql: String = "DELETE FROM set_log WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun finishSession(
    id: Long,
    status: WorkoutStatus,
    finishedAt: Instant,
    totalVolumeKg: Double?,
  ) {
    val _sql: String = "UPDATE workout_session SET status = ?, finishedAt = ?, totalVolumeKg = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: String = __griffGymConverters.workoutStatusToString(status)
        _stmt.bindText(_argIndex, _tmp)
        _argIndex = 2
        val _tmp_1: Long? = __griffGymConverters.instantToEpochMillis(finishedAt)
        if (_tmp_1 == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindLong(_argIndex, _tmp_1)
        }
        _argIndex = 3
        if (totalVolumeKg == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindDouble(_argIndex, totalVolumeKg)
        }
        _argIndex = 4
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateNotes(id: Long, notes: String?) {
    val _sql: String = "UPDATE workout_session SET notes = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        if (notes == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, notes)
        }
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
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

  private fun __fetchRelationshipsetLogAscomGriffgymInfrastructureDatabaseEntitySetLogEntity(_connection: SQLiteConnection, _map: LongSparseArray<MutableList<SetLogEntity>>) {
    if (_map.isEmpty()) {
      return
    }
    if (_map.size() > 999) {
      recursiveFetchLongSparseArray(_map, true) { _tmpMap ->
        __fetchRelationshipsetLogAscomGriffgymInfrastructureDatabaseEntitySetLogEntity(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`exerciseLogId`,`position`,`plannedWeightKg`,`plannedReps`,`plannedRpeMin`,`plannedRpeMax`,`actualWeightKg`,`actualReps`,`actualRpe`,`completed`,`notes` FROM `set_log` WHERE `exerciseLogId` IN (")
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
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "exerciseLogId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfExerciseLogId: Int = 1
      val _columnIndexOfPosition: Int = 2
      val _columnIndexOfPlannedWeightKg: Int = 3
      val _columnIndexOfPlannedReps: Int = 4
      val _columnIndexOfPlannedRpeMin: Int = 5
      val _columnIndexOfPlannedRpeMax: Int = 6
      val _columnIndexOfActualWeightKg: Int = 7
      val _columnIndexOfActualReps: Int = 8
      val _columnIndexOfActualRpe: Int = 9
      val _columnIndexOfCompleted: Int = 10
      val _columnIndexOfNotes: Int = 11
      while (_stmt.step()) {
        val _tmpKey: Long
        _tmpKey = _stmt.getLong(_itemKeyIndex)
        val _tmpRelation: MutableList<SetLogEntity>? = _map.get(_tmpKey)
        if (_tmpRelation != null) {
          val _item_1: SetLogEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpExerciseLogId: Long
          _tmpExerciseLogId = _stmt.getLong(_columnIndexOfExerciseLogId)
          val _tmpPosition: Int
          _tmpPosition = _stmt.getLong(_columnIndexOfPosition).toInt()
          val _tmpPlannedWeightKg: Double?
          if (_stmt.isNull(_columnIndexOfPlannedWeightKg)) {
            _tmpPlannedWeightKg = null
          } else {
            _tmpPlannedWeightKg = _stmt.getDouble(_columnIndexOfPlannedWeightKg)
          }
          val _tmpPlannedReps: Int?
          if (_stmt.isNull(_columnIndexOfPlannedReps)) {
            _tmpPlannedReps = null
          } else {
            _tmpPlannedReps = _stmt.getLong(_columnIndexOfPlannedReps).toInt()
          }
          val _tmpPlannedRpeMin: Double?
          if (_stmt.isNull(_columnIndexOfPlannedRpeMin)) {
            _tmpPlannedRpeMin = null
          } else {
            _tmpPlannedRpeMin = _stmt.getDouble(_columnIndexOfPlannedRpeMin)
          }
          val _tmpPlannedRpeMax: Double?
          if (_stmt.isNull(_columnIndexOfPlannedRpeMax)) {
            _tmpPlannedRpeMax = null
          } else {
            _tmpPlannedRpeMax = _stmt.getDouble(_columnIndexOfPlannedRpeMax)
          }
          val _tmpActualWeightKg: Double?
          if (_stmt.isNull(_columnIndexOfActualWeightKg)) {
            _tmpActualWeightKg = null
          } else {
            _tmpActualWeightKg = _stmt.getDouble(_columnIndexOfActualWeightKg)
          }
          val _tmpActualReps: Int?
          if (_stmt.isNull(_columnIndexOfActualReps)) {
            _tmpActualReps = null
          } else {
            _tmpActualReps = _stmt.getLong(_columnIndexOfActualReps).toInt()
          }
          val _tmpActualRpe: Double?
          if (_stmt.isNull(_columnIndexOfActualRpe)) {
            _tmpActualRpe = null
          } else {
            _tmpActualRpe = _stmt.getDouble(_columnIndexOfActualRpe)
          }
          val _tmpCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfCompleted).toInt()
          _tmpCompleted = _tmp != 0
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _item_1 = SetLogEntity(_tmpId,_tmpExerciseLogId,_tmpPosition,_tmpPlannedWeightKg,_tmpPlannedReps,_tmpPlannedRpeMin,_tmpPlannedRpeMax,_tmpActualWeightKg,_tmpActualReps,_tmpActualRpe,_tmpCompleted,_tmpNotes)
          _tmpRelation.add(_item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  private fun __fetchRelationshipexerciseLogAscomGriffgymInfrastructureDatabaseRelationExerciseLogWithDetails(_connection: SQLiteConnection, _map: LongSparseArray<MutableList<ExerciseLogWithDetails>>) {
    if (_map.isEmpty()) {
      return
    }
    if (_map.size() > 999) {
      recursiveFetchLongSparseArray(_map, true) { _tmpMap ->
        __fetchRelationshipexerciseLogAscomGriffgymInfrastructureDatabaseRelationExerciseLogWithDetails(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`sessionId`,`exerciseId`,`type`,`position` FROM `exercise_log` WHERE `sessionId` IN (")
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
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "sessionId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfSessionId: Int = 1
      val _columnIndexOfExerciseId: Int = 2
      val _columnIndexOfType: Int = 3
      val _columnIndexOfPosition: Int = 4
      val _collectionExercise: LongSparseArray<ExerciseEntity?> = LongSparseArray<ExerciseEntity?>()
      val _collectionSets: LongSparseArray<MutableList<SetLogEntity>> = LongSparseArray<MutableList<SetLogEntity>>()
      while (_stmt.step()) {
        val _tmpKey: Long
        _tmpKey = _stmt.getLong(_columnIndexOfExerciseId)
        _collectionExercise.put(_tmpKey, null)
        val _tmpKey_1: Long
        _tmpKey_1 = _stmt.getLong(_columnIndexOfId)
        if (!_collectionSets.containsKey(_tmpKey_1)) {
          _collectionSets.put(_tmpKey_1, mutableListOf())
        }
      }
      _stmt.reset()
      __fetchRelationshipexerciseAscomGriffgymInfrastructureDatabaseEntityExerciseEntity(_connection, _collectionExercise)
      __fetchRelationshipsetLogAscomGriffgymInfrastructureDatabaseEntitySetLogEntity(_connection, _collectionSets)
      while (_stmt.step()) {
        val _tmpKey_2: Long
        _tmpKey_2 = _stmt.getLong(_itemKeyIndex)
        val _tmpRelation: MutableList<ExerciseLogWithDetails>? = _map.get(_tmpKey_2)
        if (_tmpRelation != null) {
          val _item_1: ExerciseLogWithDetails
          val _tmpLog: ExerciseLogEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpSessionId: Long
          _tmpSessionId = _stmt.getLong(_columnIndexOfSessionId)
          val _tmpExerciseId: Long
          _tmpExerciseId = _stmt.getLong(_columnIndexOfExerciseId)
          val _tmpType: ExerciseType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfType)
          _tmpType = __griffGymConverters.stringToExerciseType(_tmp)
          val _tmpPosition: Int
          _tmpPosition = _stmt.getLong(_columnIndexOfPosition).toInt()
          _tmpLog = ExerciseLogEntity(_tmpId,_tmpSessionId,_tmpExerciseId,_tmpType,_tmpPosition)
          val _tmpExercise: ExerciseEntity?
          val _tmpKey_3: Long
          _tmpKey_3 = _stmt.getLong(_columnIndexOfExerciseId)
          _tmpExercise = _collectionExercise.get(_tmpKey_3)
          if (_tmpExercise == null) {
            error("Relationship item 'exercise' was expected to be NON-NULL but is NULL in @Relation involving a parent column named 'exerciseId' and entityColumn named 'id'.")
          }
          val _tmpSetsCollection: MutableList<SetLogEntity>
          val _tmpKey_4: Long
          _tmpKey_4 = _stmt.getLong(_columnIndexOfId)
          _tmpSetsCollection = checkNotNull(_collectionSets.get(_tmpKey_4))
          _item_1 = ExerciseLogWithDetails(_tmpLog,_tmpExercise,_tmpSetsCollection)
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
