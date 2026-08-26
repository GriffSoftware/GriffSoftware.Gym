package com.griffgym.presentation.home;

import com.griffgym.application.referencemax.GetReferenceMaxesUseCase;
import com.griffgym.application.referencemax.UpdateReferenceMaxUseCase;
import com.griffgym.application.stats.GetTrainingConsistencyUseCase;
import com.griffgym.application.workout.GetCurrentWorkoutUseCase;
import com.griffgym.application.workout.StartWorkoutUseCase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.time.Clock;
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
public final class HomeViewModel_Factory implements Factory<HomeViewModel> {
  private final Provider<GetCurrentWorkoutUseCase> getCurrentWorkoutProvider;

  private final Provider<StartWorkoutUseCase> startWorkoutProvider;

  private final Provider<GetReferenceMaxesUseCase> getReferenceMaxesProvider;

  private final Provider<UpdateReferenceMaxUseCase> updateReferenceMaxProvider;

  private final Provider<GetTrainingConsistencyUseCase> getTrainingConsistencyProvider;

  private final Provider<Clock> clockProvider;

  private HomeViewModel_Factory(Provider<GetCurrentWorkoutUseCase> getCurrentWorkoutProvider,
      Provider<StartWorkoutUseCase> startWorkoutProvider,
      Provider<GetReferenceMaxesUseCase> getReferenceMaxesProvider,
      Provider<UpdateReferenceMaxUseCase> updateReferenceMaxProvider,
      Provider<GetTrainingConsistencyUseCase> getTrainingConsistencyProvider,
      Provider<Clock> clockProvider) {
    this.getCurrentWorkoutProvider = getCurrentWorkoutProvider;
    this.startWorkoutProvider = startWorkoutProvider;
    this.getReferenceMaxesProvider = getReferenceMaxesProvider;
    this.updateReferenceMaxProvider = updateReferenceMaxProvider;
    this.getTrainingConsistencyProvider = getTrainingConsistencyProvider;
    this.clockProvider = clockProvider;
  }

  @Override
  public HomeViewModel get() {
    return newInstance(getCurrentWorkoutProvider.get(), startWorkoutProvider.get(), getReferenceMaxesProvider.get(), updateReferenceMaxProvider.get(), getTrainingConsistencyProvider.get(), clockProvider.get());
  }

  public static HomeViewModel_Factory create(
      Provider<GetCurrentWorkoutUseCase> getCurrentWorkoutProvider,
      Provider<StartWorkoutUseCase> startWorkoutProvider,
      Provider<GetReferenceMaxesUseCase> getReferenceMaxesProvider,
      Provider<UpdateReferenceMaxUseCase> updateReferenceMaxProvider,
      Provider<GetTrainingConsistencyUseCase> getTrainingConsistencyProvider,
      Provider<Clock> clockProvider) {
    return new HomeViewModel_Factory(getCurrentWorkoutProvider, startWorkoutProvider, getReferenceMaxesProvider, updateReferenceMaxProvider, getTrainingConsistencyProvider, clockProvider);
  }

  public static HomeViewModel newInstance(GetCurrentWorkoutUseCase getCurrentWorkout,
      StartWorkoutUseCase startWorkout, GetReferenceMaxesUseCase getReferenceMaxes,
      UpdateReferenceMaxUseCase updateReferenceMax,
      GetTrainingConsistencyUseCase getTrainingConsistency, Clock clock) {
    return new HomeViewModel(getCurrentWorkout, startWorkout, getReferenceMaxes, updateReferenceMax, getTrainingConsistency, clock);
  }
}
