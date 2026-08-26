package com.griffgym.infrastructure.repository;

import com.griffgym.infrastructure.database.GriffGymDatabase;
import com.griffgym.infrastructure.database.dao.WorkoutSessionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class RoomWorkoutSessionRepository_Factory implements Factory<RoomWorkoutSessionRepository> {
  private final Provider<GriffGymDatabase> databaseProvider;

  private final Provider<WorkoutSessionDao> sessionDaoProvider;

  private RoomWorkoutSessionRepository_Factory(Provider<GriffGymDatabase> databaseProvider,
      Provider<WorkoutSessionDao> sessionDaoProvider) {
    this.databaseProvider = databaseProvider;
    this.sessionDaoProvider = sessionDaoProvider;
  }

  @Override
  public RoomWorkoutSessionRepository get() {
    return newInstance(databaseProvider.get(), sessionDaoProvider.get());
  }

  public static RoomWorkoutSessionRepository_Factory create(
      Provider<GriffGymDatabase> databaseProvider, Provider<WorkoutSessionDao> sessionDaoProvider) {
    return new RoomWorkoutSessionRepository_Factory(databaseProvider, sessionDaoProvider);
  }

  public static RoomWorkoutSessionRepository newInstance(GriffGymDatabase database,
      WorkoutSessionDao sessionDao) {
    return new RoomWorkoutSessionRepository(database, sessionDao);
  }
}
