package com.griffgym.presentation.history;

import com.griffgym.application.workout.GetWorkoutHistoryUseCase;
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
public final class HistoryViewModel_Factory implements Factory<HistoryViewModel> {
  private final Provider<GetWorkoutHistoryUseCase> getWorkoutHistoryProvider;

  private HistoryViewModel_Factory(Provider<GetWorkoutHistoryUseCase> getWorkoutHistoryProvider) {
    this.getWorkoutHistoryProvider = getWorkoutHistoryProvider;
  }

  @Override
  public HistoryViewModel get() {
    return newInstance(getWorkoutHistoryProvider.get());
  }

  public static HistoryViewModel_Factory create(
      Provider<GetWorkoutHistoryUseCase> getWorkoutHistoryProvider) {
    return new HistoryViewModel_Factory(getWorkoutHistoryProvider);
  }

  public static HistoryViewModel newInstance(GetWorkoutHistoryUseCase getWorkoutHistory) {
    return new HistoryViewModel(getWorkoutHistory);
  }
}
