package com.griffgym.infrastructure.repository;

import com.griffgym.infrastructure.database.dao.ReferenceMaxDao;
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
public final class RoomReferenceMaxRepository_Factory implements Factory<RoomReferenceMaxRepository> {
  private final Provider<ReferenceMaxDao> referenceMaxDaoProvider;

  private RoomReferenceMaxRepository_Factory(Provider<ReferenceMaxDao> referenceMaxDaoProvider) {
    this.referenceMaxDaoProvider = referenceMaxDaoProvider;
  }

  @Override
  public RoomReferenceMaxRepository get() {
    return newInstance(referenceMaxDaoProvider.get());
  }

  public static RoomReferenceMaxRepository_Factory create(
      Provider<ReferenceMaxDao> referenceMaxDaoProvider) {
    return new RoomReferenceMaxRepository_Factory(referenceMaxDaoProvider);
  }

  public static RoomReferenceMaxRepository newInstance(ReferenceMaxDao referenceMaxDao) {
    return new RoomReferenceMaxRepository(referenceMaxDao);
  }
}
