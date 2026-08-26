package com.griffgym.presentation.workout;

import androidx.lifecycle.SavedStateHandle;
import com.griffgym.application.exercise.GetExercisesUseCase;
import com.griffgym.application.workout.AddExerciseToWorkoutUseCase;
import com.griffgym.application.workout.AddSetUseCase;
import com.griffgym.application.workout.CancelWorkoutUseCase;
import com.griffgym.application.workout.CompleteWorkoutUseCase;
import com.griffgym.application.workout.GetCurrentWorkoutUseCase;
import com.griffgym.application.workout.GetWorkoutSessionUseCase;
import com.griffgym.application.workout.RemoveSetUseCase;
import com.griffgym.application.workout.SaveSetResultUseCase;
import com.griffgym.application.workout.StartWorkoutUseCase;
import com.griffgym.application.workout.UpdateSetResultUseCase;
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
public final class WorkoutViewModel_Factory implements Factory<WorkoutViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<GetCurrentWorkoutUseCase> getCurrentWorkoutProvider;

  private final Provider<GetWorkoutSessionUseCase> getWorkoutSessionProvider;

  private final Provider<GetExercisesUseCase> getExercisesProvider;

  private final Provider<StartWorkoutUseCase> startWorkoutProvider;

  private final Provider<UpdateSetResultUseCase> updateSetResultProvider;

  private final Provider<SaveSetResultUseCase> saveSetResultProvider;

  private final Provider<AddSetUseCase> addSetUseCaseProvider;

  private final Provider<RemoveSetUseCase> removeSetUseCaseProvider;

  private final Provider<AddExerciseToWorkoutUseCase> addExerciseUseCaseProvider;

  private final Provider<CompleteWorkoutUseCase> completeWorkoutProvider;

  private final Provider<CancelWorkoutUseCase> cancelWorkoutProvider;

  private WorkoutViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetCurrentWorkoutUseCase> getCurrentWorkoutProvider,
      Provider<GetWorkoutSessionUseCase> getWorkoutSessionProvider,
      Provider<GetExercisesUseCase> getExercisesProvider,
      Provider<StartWorkoutUseCase> startWorkoutProvider,
      Provider<UpdateSetResultUseCase> updateSetResultProvider,
      Provider<SaveSetResultUseCase> saveSetResultProvider,
      Provider<AddSetUseCase> addSetUseCaseProvider,
      Provider<RemoveSetUseCase> removeSetUseCaseProvider,
      Provider<AddExerciseToWorkoutUseCase> addExerciseUseCaseProvider,
      Provider<CompleteWorkoutUseCase> completeWorkoutProvider,
      Provider<CancelWorkoutUseCase> cancelWorkoutProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.getCurrentWorkoutProvider = getCurrentWorkoutProvider;
    this.getWorkoutSessionProvider = getWorkoutSessionProvider;
    this.getExercisesProvider = getExercisesProvider;
    this.startWorkoutProvider = startWorkoutProvider;
    this.updateSetResultProvider = updateSetResultProvider;
    this.saveSetResultProvider = saveSetResultProvider;
    this.addSetUseCaseProvider = addSetUseCaseProvider;
    this.removeSetUseCaseProvider = removeSetUseCaseProvider;
    this.addExerciseUseCaseProvider = addExerciseUseCaseProvider;
    this.completeWorkoutProvider = completeWorkoutProvider;
    this.cancelWorkoutProvider = cancelWorkoutProvider;
  }

  @Override
  public WorkoutViewModel get() {
    return newInstance(savedStateHandleProvider.get(), getCurrentWorkoutProvider.get(), getWorkoutSessionProvider.get(), getExercisesProvider.get(), startWorkoutProvider.get(), updateSetResultProvider.get(), saveSetResultProvider.get(), addSetUseCaseProvider.get(), removeSetUseCaseProvider.get(), addExerciseUseCaseProvider.get(), completeWorkoutProvider.get(), cancelWorkoutProvider.get());
  }

  public static WorkoutViewModel_Factory create(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetCurrentWorkoutUseCase> getCurrentWorkoutProvider,
      Provider<GetWorkoutSessionUseCase> getWorkoutSessionProvider,
      Provider<GetExercisesUseCase> getExercisesProvider,
      Provider<StartWorkoutUseCase> startWorkoutProvider,
      Provider<UpdateSetResultUseCase> updateSetResultProvider,
      Provider<SaveSetResultUseCase> saveSetResultProvider,
      Provider<AddSetUseCase> addSetUseCaseProvider,
      Provider<RemoveSetUseCase> removeSetUseCaseProvider,
      Provider<AddExerciseToWorkoutUseCase> addExerciseUseCaseProvider,
      Provider<CompleteWorkoutUseCase> completeWorkoutProvider,
      Provider<CancelWorkoutUseCase> cancelWorkoutProvider) {
    return new WorkoutViewModel_Factory(savedStateHandleProvider, getCurrentWorkoutProvider, getWorkoutSessionProvider, getExercisesProvider, startWorkoutProvider, updateSetResultProvider, saveSetResultProvider, addSetUseCaseProvider, removeSetUseCaseProvider, addExerciseUseCaseProvider, completeWorkoutProvider, cancelWorkoutProvider);
  }

  public static WorkoutViewModel newInstance(SavedStateHandle savedStateHandle,
      GetCurrentWorkoutUseCase getCurrentWorkout, GetWorkoutSessionUseCase getWorkoutSession,
      GetExercisesUseCase getExercises, StartWorkoutUseCase startWorkout,
      UpdateSetResultUseCase updateSetResult, SaveSetResultUseCase saveSetResult,
      AddSetUseCase addSetUseCase, RemoveSetUseCase removeSetUseCase,
      AddExerciseToWorkoutUseCase addExerciseUseCase, CompleteWorkoutUseCase completeWorkout,
      CancelWorkoutUseCase cancelWorkout) {
    return new WorkoutViewModel(savedStateHandle, getCurrentWorkout, getWorkoutSession, getExercises, startWorkout, updateSetResult, saveSetResult, addSetUseCase, removeSetUseCase, addExerciseUseCase, completeWorkout, cancelWorkout);
  }
}
