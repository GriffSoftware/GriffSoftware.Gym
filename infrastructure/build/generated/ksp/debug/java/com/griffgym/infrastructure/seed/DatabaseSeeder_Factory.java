package com.griffgym.infrastructure.seed;

import com.griffgym.infrastructure.database.GriffGymDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.time.Clock;
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
public final class DatabaseSeeder_Factory implements Factory<DatabaseSeeder> {
  private final Provider<GriffGymDatabase> databaseProvider;

  private final Provider<Clock> clockProvider;

  private DatabaseSeeder_Factory(Provider<GriffGymDatabase> databaseProvider,
      Provider<Clock> clockProvider) {
    this.databaseProvider = databaseProvider;
    this.clockProvider = clockProvider;
  }

  @Override
  public DatabaseSeeder get() {
    return newInstance(databaseProvider.get(), clockProvider.get());
  }

  public static DatabaseSeeder_Factory create(Provider<GriffGymDatabase> databaseProvider,
      Provider<Clock> clockProvider) {
    return new DatabaseSeeder_Factory(databaseProvider, clockProvider);
  }

  public static DatabaseSeeder newInstance(GriffGymDatabase database, Clock clock) {
    return new DatabaseSeeder(database, clock);
  }
}
