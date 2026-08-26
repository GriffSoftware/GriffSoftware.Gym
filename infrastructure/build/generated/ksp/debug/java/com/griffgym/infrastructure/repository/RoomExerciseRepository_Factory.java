package com.griffgym.infrastructure.repository;

import com.griffgym.infrastructure.database.dao.ExerciseDao;
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
public final class RoomExerciseRepository_Factory implements Factory<RoomExerciseRepository> {
  private final Provider<ExerciseDao> exerciseDaoProvider;

  private RoomExerciseRepository_Factory(Provider<ExerciseDao> exerciseDaoProvider) {
    this.exerciseDaoProvider = exerciseDaoProvider;
  }

  @Override
  public RoomExerciseRepository get() {
    return newInstance(exerciseDaoProvider.get());
  }

  public static RoomExerciseRepository_Factory create(Provider<ExerciseDao> exerciseDaoProvider) {
    return new RoomExerciseRepository_Factory(exerciseDaoProvider);
  }

  public static RoomExerciseRepository newInstance(ExerciseDao exerciseDao) {
    return new RoomExerciseRepository(exerciseDao);
  }
}
