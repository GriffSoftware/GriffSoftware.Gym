package com.griffgym.infrastructure.di

import android.content.Context
import androidx.room.Room
import com.griffgym.domain.repository.ExerciseRepository
import com.griffgym.domain.repository.ReferenceMaxRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.dao.ExerciseDao
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao
import com.griffgym.infrastructure.database.dao.TrainingProgramDao
import com.griffgym.infrastructure.database.dao.WorkoutSessionDao
import com.griffgym.infrastructure.repository.RoomExerciseRepository
import com.griffgym.infrastructure.repository.RoomReferenceMaxRepository
import com.griffgym.infrastructure.repository.RoomTrainingProgramRepository
import com.griffgym.infrastructure.repository.RoomWorkoutSessionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GriffGymDatabase =
        Room.databaseBuilder(context, GriffGymDatabase::class.java, GriffGymDatabase.NAME)
            // Foreign keys are declared on the entities; SQLite still needs them switched on.
            .build()

    @Provides
    fun provideExerciseDao(database: GriffGymDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideTrainingProgramDao(database: GriffGymDatabase): TrainingProgramDao =
        database.trainingProgramDao()

    @Provides
    fun provideWorkoutSessionDao(database: GriffGymDatabase): WorkoutSessionDao =
        database.workoutSessionDao()

    @Provides
    fun provideReferenceMaxDao(database: GriffGymDatabase): ReferenceMaxDao =
        database.referenceMaxDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindExerciseRepository(impl: RoomExerciseRepository): ExerciseRepository

    @Binds
    abstract fun bindTrainingProgramRepository(
        impl: RoomTrainingProgramRepository,
    ): TrainingProgramRepository

    @Binds
    abstract fun bindWorkoutSessionRepository(
        impl: RoomWorkoutSessionRepository,
    ): WorkoutSessionRepository

    @Binds
    abstract fun bindReferenceMaxRepository(impl: RoomReferenceMaxRepository): ReferenceMaxRepository
}
