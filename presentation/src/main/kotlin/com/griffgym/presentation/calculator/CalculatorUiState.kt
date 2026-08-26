package com.griffgym.presentation.calculator

import androidx.compose.runtime.Immutable
import com.griffgym.domain.model.ExerciseCategory

@Immutable
data class CalculatorUiState(
    val weightInput: String = "100",
    val reps: Int = 5,
    val result: CalculatorResult? = null,
    val percentages: List<PercentageRow> = emptyList(),
    val referenceMaxes: List<ReferenceMaxOption> = emptyList(),
    val showMaxPicker: Boolean = false,
    val error: String? = null,
)

@Immutable
data class CalculatorResult(
    val oneRepMax: String,
    val isReliable: Boolean,
)

@Immutable
data class PercentageRow(
    val percent: String,
    val weight: String,
    val reps: String,
    val isMax: Boolean,
)

@Immutable
data class ReferenceMaxOption(
    val category: ExerciseCategory,
    val label: String,
    val weight: String,
)

sealed interface CalculatorUiEvent {
    data class WeightChanged(val value: String) : CalculatorUiEvent
    data class RepsChanged(val value: Int) : CalculatorUiEvent
    data object Calculate : CalculatorUiEvent
    data object OpenMaxPicker : CalculatorUiEvent
    data object DismissMaxPicker : CalculatorUiEvent
    data class UseReferenceMax(val category: ExerciseCategory) : CalculatorUiEvent
}
