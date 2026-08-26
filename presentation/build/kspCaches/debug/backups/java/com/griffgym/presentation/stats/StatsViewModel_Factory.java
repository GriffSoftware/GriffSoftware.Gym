package com.griffgym.presentation.stats;

import com.griffgym.application.stats.GetOneRepMaxHistoryUseCase;
import com.griffgym.application.stats.GetPersonalRecordsUseCase;
import com.griffgym.application.stats.GetTrainingConsistencyUseCase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class StatsViewModel_Factory implements Factory<StatsViewModel> {
  private final Provider<GetOneRepMaxHistoryUseCase> getOneRepMaxHistoryProvider;

  private final Provider<GetPersonalRecordsUseCase> getPersonalRecordsProvider;

  private final Provider<GetTrainingConsistencyUseCase> getTrainingConsistencyProvider;

  private StatsViewModel_Factory(Provider<GetOneRepMaxHistoryUseCase> getOneRepMaxHistoryProvider,
      Provider<GetPersonalRecordsUseCase> getPersonalRecordsProvider,
      Provider<GetTrainingConsistencyUseCase> getTrainingConsistencyProvider) {
    this.getOneRepMaxHistoryProvider = getOneRepMaxHistoryProvider;
    this.getPersonalRecordsProvider = getPersonalRecordsProvider;
    this.getTrainingConsistencyProvider = getTrainingConsistencyProvider;
  }

  @Override
  public StatsViewModel get() {
    return newInstance(getOneRepMaxHistoryProvider.get(), getPersonalRecordsProvider.get(), getTrainingConsistencyProvider.get());
  }

  public static StatsViewModel_Factory create(
      Provider<GetOneRepMaxHistoryUseCase> getOneRepMaxHistoryProvider,
      Provider<GetPersonalRecordsUseCase> getPersonalRecordsProvider,
      Provider<GetTrainingConsistencyUseCase> getTrainingConsistencyProvider) {
    return new StatsViewModel_Factory(getOneRepMaxHistoryProvider, getPersonalRecordsProvider, getTrainingConsistencyProvider);
  }

  public static StatsViewModel newInstance(GetOneRepMaxHistoryUseCase getOneRepMaxHistory,
      GetPersonalRecordsUseCase getPersonalRecords,
      GetTrainingConsistencyUseCase getTrainingConsistency) {
    return new StatsViewModel(getOneRepMaxHistory, getPersonalRecords, getTrainingConsistency);
  }
}
