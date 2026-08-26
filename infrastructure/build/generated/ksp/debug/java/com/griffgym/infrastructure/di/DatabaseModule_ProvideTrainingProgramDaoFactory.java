package com.griffgym.infrastructure.di;

import com.griffgym.infrastructure.database.GriffGymDatabase;
import com.griffgym.infrastructure.database.dao.TrainingProgramDao;
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
public final class DatabaseModule_ProvideTrainingProgramDaoFactory implements Factory<TrainingProgramDao> {
  private final Provider<GriffGymDatabase> databaseProvider;

  private DatabaseModule_ProvideTrainingProgramDaoFactory(
      Provider<GriffGymDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public TrainingProgramDao get() {
    return provideTrainingProgramDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideTrainingProgramDaoFactory create(
      Provider<GriffGymDatabase> databaseProvider) {
    return new DatabaseModule_ProvideTrainingProgramDaoFactory(databaseProvider);
  }

  public static TrainingProgramDao provideTrainingProgramDao(GriffGymDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideTrainingProgramDao(database));
  }
}
