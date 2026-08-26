package com.griffgym.infrastructure.di;

import com.griffgym.infrastructure.database.GriffGymDatabase;
import com.griffgym.infrastructure.database.dao.WorkoutSessionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class DatabaseModule_ProvideWorkoutSessionDaoFactory implements Factory<WorkoutSessionDao> {
  private final Provider<GriffGymDatabase> databaseProvider;

  private DatabaseModule_ProvideWorkoutSessionDaoFactory(
      Provider<GriffGymDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public WorkoutSessionDao get() {
    return provideWorkoutSessionDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideWorkoutSessionDaoFactory create(
      Provider<GriffGymDatabase> databaseProvider) {
    return new DatabaseModule_ProvideWorkoutSessionDaoFactory(databaseProvider);
  }

  public static WorkoutSessionDao provideWorkoutSessionDao(GriffGymDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideWorkoutSessionDao(database));
  }
}
