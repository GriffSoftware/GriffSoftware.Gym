package com.griffgym.infrastructure.database.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.infrastructure.database.converter.GriffGymConverters
import com.griffgym.infrastructure.database.entity.ReferenceMaxEntity
import javax.`annotation`.processing.Generated
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
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ReferenceMaxDao_Impl(
  __db: RoomDatabase,
) : ReferenceMaxDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfReferenceMaxEntity: EntityInsertAdapter<ReferenceMaxEntity>

  private val __griffGymConverters: GriffGymConverters = GriffGymConverters()
  init {
    this.__db = __db
    this.__insertAdapterOfReferenceMaxEntity = object : EntityInsertAdapter<ReferenceMaxEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `reference_max` (`category`,`weightKg`,`updatedOn`) VALUES (?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ReferenceMaxEntity) {
        val _tmp: String = __griffGymConverters.exerciseCategoryToString(entity.category)
        statement.bindText(1, _tmp)
        statement.bindDouble(2, entity.weightKg)
        statement.bindLong(3, entity.updatedOn)
      }
    }
  }

  public override suspend fun upsert(referenceMax: ReferenceMaxEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfReferenceMaxEntity.insert(_connection, referenceMax)
  }

  public override suspend fun upsertAll(referenceMaxes: List<ReferenceMaxEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfReferenceMaxEntity.insert(_connection, referenceMaxes)
  }

  public override fun observeAll(): Flow<List<ReferenceMaxEntity>> {
    val _sql: String = "SELECT * FROM reference_max"
    return createFlow(__db, false, arrayOf("reference_max")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfWeightKg: Int = getColumnIndexOrThrow(_stmt, "weightKg")
        val _columnIndexOfUpdatedOn: Int = getColumnIndexOrThrow(_stmt, "updatedOn")
        val _result: MutableList<ReferenceMaxEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ReferenceMaxEntity
          val _tmpCategory: ExerciseCategory
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfCategory)
          _tmpCategory = __griffGymConverters.stringToExerciseCategory(_tmp)
          val _tmpWeightKg: Double
          _tmpWeightKg = _stmt.getDouble(_columnIndexOfWeightKg)
          val _tmpUpdatedOn: Long
          _tmpUpdatedOn = _stmt.getLong(_columnIndexOfUpdatedOn)
          _item = ReferenceMaxEntity(_tmpCategory,_tmpWeightKg,_tmpUpdatedOn)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByCategory(category: ExerciseCategory): ReferenceMaxEntity? {
    val _sql: String = "SELECT * FROM reference_max WHERE category = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: String = __griffGymConverters.exerciseCategoryToString(category)
        _stmt.bindText(_argIndex, _tmp)
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfWeightKg: Int = getColumnIndexOrThrow(_stmt, "weightKg")
        val _columnIndexOfUpdatedOn: Int = getColumnIndexOrThrow(_stmt, "updatedOn")
        val _result: ReferenceMaxEntity?
        if (_stmt.step()) {
          val _tmpCategory: ExerciseCategory
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfCategory)
          _tmpCategory = __griffGymConverters.stringToExerciseCategory(_tmp_1)
          val _tmpWeightKg: Double
          _tmpWeightKg = _stmt.getDouble(_columnIndexOfWeightKg)
          val _tmpUpdatedOn: Long
          _tmpUpdatedOn = _stmt.getLong(_columnIndexOfUpdatedOn)
          _result = ReferenceMaxEntity(_tmpCategory,_tmpWeightKg,_tmpUpdatedOn)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun count(): Int {
    val _sql: String = "SELECT COUNT(*) FROM reference_max"
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

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
