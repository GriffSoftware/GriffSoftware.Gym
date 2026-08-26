package com.griffgym.infrastructure.di;

import com.griffgym.infrastructure.database.GriffGymDatabase;
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao;
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
public final class DatabaseModule_ProvideReferenceMaxDaoFactory implements Factory<ReferenceMaxDao> {
  private final Provider<GriffGymDatabase> databaseProvider;

  private DatabaseModule_ProvideReferenceMaxDaoFactory(
      Provider<GriffGymDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ReferenceMaxDao get() {
    return provideReferenceMaxDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideReferenceMaxDaoFactory create(
      Provider<GriffGymDatabase> databaseProvider) {
    return new DatabaseModule_ProvideReferenceMaxDaoFactory(databaseProvider);
  }

  public static ReferenceMaxDao provideReferenceMaxDao(GriffGymDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideReferenceMaxDao(database));
  }
}
