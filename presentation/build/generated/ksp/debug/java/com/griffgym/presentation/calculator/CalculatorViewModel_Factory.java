package com.griffgym.presentation.calculator;

import androidx.lifecycle.SavedStateHandle;
import com.griffgym.application.metrics.CalculateEstimated1RmUseCase;
import com.griffgym.application.metrics.GetTrainingPercentagesUseCase;
import com.griffgym.application.referencemax.GetReferenceMaxesUseCase;
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
public final class CalculatorViewModel_Factory implements Factory<CalculatorViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<CalculateEstimated1RmUseCase> calculateEstimated1RmProvider;

  private final Provider<GetTrainingPercentagesUseCase> getTrainingPercentagesProvider;

  private final Provider<GetReferenceMaxesUseCase> getReferenceMaxesProvider;

  private CalculatorViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<CalculateEstimated1RmUseCase> calculateEstimated1RmProvider,
      Provider<GetTrainingPercentagesUseCase> getTrainingPercentagesProvider,
      Provider<GetReferenceMaxesUseCase> getReferenceMaxesProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.calculateEstimated1RmProvider = calculateEstimated1RmProvider;
    this.getTrainingPercentagesProvider = getTrainingPercentagesProvider;
    this.getReferenceMaxesProvider = getReferenceMaxesProvider;
  }

  @Override
  public CalculatorViewModel get() {
    return newInstance(savedStateHandleProvider.get(), calculateEstimated1RmProvider.get(), getTrainingPercentagesProvider.get(), getReferenceMaxesProvider.get());
  }

  public static CalculatorViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<CalculateEstimated1RmUseCase> calculateEstimated1RmProvider,
      Provider<GetTrainingPercentagesUseCase> getTrainingPercentagesProvider,
      Provider<GetReferenceMaxesUseCase> getReferenceMaxesProvider) {
    return new CalculatorViewModel_Factory(savedStateHandleProvider, calculateEstimated1RmProvider, getTrainingPercentagesProvider, getReferenceMaxesProvider);
  }

  public static CalculatorViewModel newInstance(SavedStateHandle savedStateHandle,
      CalculateEstimated1RmUseCase calculateEstimated1Rm,
      GetTrainingPercentagesUseCase getTrainingPercentages,
      GetReferenceMaxesUseCase getReferenceMaxes) {
    return new CalculatorViewModel(savedStateHandle, calculateEstimated1Rm, getTrainingPercentages, getReferenceMaxes);
  }
}
