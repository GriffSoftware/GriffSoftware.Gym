package com.griffgym.infrastructure.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.griffgym.infrastructure.database.converter.GriffGymConverters
import com.griffgym.infrastructure.database.dao.ExerciseDao
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao
import com.griffgym.infrastructure.database.dao.TrainingProgramDao
import com.griffgym.infrastructure.database.dao.WorkoutSessionDao
import com.griffgym.infrastructure.database.entity.ExerciseEntity
import com.griffgym.infrastructure.database.entity.ExerciseLogEntity
import com.griffgym.infrastructure.database.entity.ExerciseTemplateEntity
import com.griffgym.infrastructure.database.entity.PlannedSetEntity
import com.griffgym.infrastructure.database.entity.ProgramProgressEntity
import com.griffgym.infrastructure.database.entity.ReferenceMaxEntity
import com.griffgym.infrastructure.database.entity.SetLogEntity
import com.griffgym.infrastructure.database.entity.TrainingProgramEntity
import com.griffgym.infrastructure.database.entity.TrainingWeekEntity
import com.griffgym.infrastructure.database.entity.WorkoutSessionEntity
import com.griffgym.infrastructure.database.entity.WorkoutTemplateEntity

@Database(
    entities = [
        ExerciseEntity::class,
        TrainingProgramEntity::class,
        TrainingWeekEntity::class,
        WorkoutTemplateEntity::class,
        ExerciseTemplateEntity::class,
        PlannedSetEntity::class,
        ProgramProgressEntity::class,
        WorkoutSessionEntity::class,
        ExerciseLogEntity::class,
        SetLogEntity::class,
        ReferenceMaxEntity::class,
    ],
    version = GriffGymDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(GriffGymConverters::class)
abstract class GriffGymDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun trainingProgramDao(): TrainingProgramDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun referenceMaxDao(): ReferenceMaxDao

    companion object {
        const val VERSION = 1
        const val NAME = "griff_gym.db"
    }
}
