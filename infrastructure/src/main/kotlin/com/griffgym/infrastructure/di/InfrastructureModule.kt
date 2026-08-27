package com.griffgym.infrastructure.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.griffgym.domain.repository.ExerciseRepository
import com.griffgym.domain.repository.OnboardingRepository
import com.griffgym.domain.repository.ReferenceMaxRepository
import com.griffgym.domain.repository.TrainingCycleRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.dao.ExerciseDao
import com.griffgym.infrastructure.database.dao.CloudSyncDao
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao
import com.griffgym.infrastructure.database.dao.SyncMetadataDao
import com.griffgym.infrastructure.database.dao.TrainingCycleDao
import com.griffgym.infrastructure.database.dao.TrainingProgramDao
import com.griffgym.infrastructure.database.dao.WorkoutSessionDao
import com.griffgym.infrastructure.database.migration.GriffGymMigrations
import com.griffgym.infrastructure.preferences.DataStoreOnboardingRepository
import com.griffgym.infrastructure.repository.RoomExerciseRepository
import com.griffgym.infrastructure.repository.RoomReferenceMaxRepository
import com.griffgym.infrastructure.repository.RoomTrainingCycleRepository
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

    /**
     * Migrations are declared explicitly and destructive fallback is deliberately absent:
     * a schema change must never be allowed to take a lifter's training history with it.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GriffGymDatabase =
        Room.databaseBuilder(context, GriffGymDatabase::class.java, GriffGymDatabase.NAME)
            .addMigrations(*GriffGymMigrations)
            .build()

    @Provides
    fun provideExerciseDao(database: GriffGymDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideTrainingProgramDao(database: GriffGymDatabase): TrainingProgramDao =
        database.trainingProgramDao()

    @Provides
    fun provideTrainingCycleDao(database: GriffGymDatabase): TrainingCycleDao =
        database.trainingCycleDao()

    @Provides
    fun provideWorkoutSessionDao(database: GriffGymDatabase): WorkoutSessionDao =
        database.workoutSessionDao()

    @Provides
    fun provideReferenceMaxDao(database: GriffGymDatabase): ReferenceMaxDao =
        database.referenceMaxDao()

    @Provides
    fun provideSyncMetadataDao(database: GriffGymDatabase): SyncMetadataDao =
        database.syncMetadataDao()

    @Provides
    fun provideCloudSyncDao(database: GriffGymDatabase): CloudSyncDao = database.cloudSyncDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /**
     * Application state only — the first run flag. Training data lives in Room, which is
     * why this store is small enough to never need a migration story of its own.
     */
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile(PREFERENCES_NAME)
    }

    private const val PREFERENCES_NAME = "griff_gym_preferences"
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
    abstract fun bindTrainingCycleRepository(
        impl: RoomTrainingCycleRepository,
    ): TrainingCycleRepository

    @Binds
    abstract fun bindWorkoutSessionRepository(
        impl: RoomWorkoutSessionRepository,
    ): WorkoutSessionRepository

    @Binds
    abstract fun bindReferenceMaxRepository(impl: RoomReferenceMaxRepository): ReferenceMaxRepository

    @Binds
    abstract fun bindOnboardingRepository(impl: DataStoreOnboardingRepository): OnboardingRepository
}
