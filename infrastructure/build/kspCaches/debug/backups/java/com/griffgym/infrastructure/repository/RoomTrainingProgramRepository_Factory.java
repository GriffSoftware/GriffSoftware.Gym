package com.griffgym.infrastructure.repository;

import com.griffgym.infrastructure.database.dao.TrainingProgramDao;
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
public final class RoomTrainingProgramRepository_Factory implements Factory<RoomTrainingProgramRepository> {
  private final Provider<TrainingProgramDao> programDaoProvider;

  private RoomTrainingProgramRepository_Factory(Provider<TrainingProgramDao> programDaoProvider) {
    this.programDaoProvider = programDaoProvider;
  }

  @Override
  public RoomTrainingProgramRepository get() {
    return newInstance(programDaoProvider.get());
  }

  public static RoomTrainingProgramRepository_Factory create(
      Provider<TrainingProgramDao> programDaoProvider) {
    return new RoomTrainingProgramRepository_Factory(programDaoProvider);
  }

  public static RoomTrainingProgramRepository newInstance(TrainingProgramDao programDao) {
    return new RoomTrainingProgramRepository(programDao);
  }
}
