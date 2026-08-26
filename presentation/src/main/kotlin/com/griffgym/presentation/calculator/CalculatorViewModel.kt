package com.griffgym.presentation.calculator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.metrics.CalculateEstimated1RmUseCase
import com.griffgym.application.metrics.GetTrainingPercentagesUseCase
import com.griffgym.application.referencemax.GetReferenceMaxesUseCase
import com.griffgym.domain.model.OneRepMaxCalculator
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.presentation.format.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val calculateEstimated1Rm: CalculateEstimated1RmUseCase,
    private val getTrainingPercentages: GetTrainingPercentagesUseCase,
    getReferenceMaxes: GetReferenceMaxesUseCase,
) : ViewModel() {

    private val form = MutableStateFlow(
        FormState(
            weight = savedStateHandle[KEY_WEIGHT] ?: "100",
            reps = savedStateHandle[KEY_REPS] ?: 5,
        ),
    )

    val uiState: StateFlow<CalculatorUiState> = combine(
        form,
        getReferenceMaxes(),
    ) { state, referenceMaxes ->
        state.toUiState(referenceMaxes)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = form.value.toUiState(emptyList()),
    )

    fun onEvent(event: CalculatorUiEvent) {
        when (event) {
            is CalculatorUiEvent.WeightChanged -> {
                savedStateHandle[KEY_WEIGHT] = event.value
                form.update { it.copy(weight = event.value, submitted = it.submitted) }
            }
            is CalculatorUiEvent.RepsChanged -> {
                savedStateHandle[KEY_REPS] = event.value
                form.update { it.copy(reps = event.value) }
            }
            CalculatorUiEvent.Calculate -> form.update { it.copy(submitted = true) }
            CalculatorUiEvent.OpenMaxPicker -> form.update { it.copy(pickerOpen = true) }
            CalculatorUiEvent.DismissMaxPicker -> form.update { it.copy(pickerOpen = false) }
            is CalculatorUiEvent.UseReferenceMax -> useReferenceMax(event)
        }
    }

    private fun useReferenceMax(event: CalculatorUiEvent.UseReferenceMax) {
        val option = uiState.value.referenceMaxes.firstOrNull { it.category == event.category }
            ?: return
        savedStateHandle[KEY_WEIGHT] = option.weight
        savedStateHandle[KEY_REPS] = 1
        form.update {
            it.copy(weight = option.weight, reps = 1, pickerOpen = false, submitted = true)
        }
    }

    private fun FormState.toUiState(referenceMaxes: List<ReferenceMax>): CalculatorUiState {
        val estimate = calculateEstimated1Rm(weight, reps)
        return CalculatorUiState(
            weightInput = weight,
            reps = reps,
            result = estimate?.let {
                CalculatorResult(
                    oneRepMax = it.weight.format(),
                    isReliable = it.isReliable,
                )
            },
            percentages = estimate?.let { result ->
                getTrainingPercentages(result.weight).map { row ->
                    PercentageRow(
                        percent = "${row.percent}%",
                        weight = row.weight.format(),
                        reps = row.formatReps(),
                        isMax = row.percent == 100,
                    )
                }
            }.orEmpty(),
            referenceMaxes = referenceMaxes.map {
                ReferenceMaxOption(
                    category = it.category,
                    label = Format.categoryLabel(it.category),
                    weight = it.weight.format(),
                )
            },
            showMaxPicker = pickerOpen,
            error = if (submitted && estimate == null) "Enter a valid weight" else null,
        )
    }

    private data class FormState(
        val weight: String,
        val reps: Int,
        val submitted: Boolean = false,
        val pickerOpen: Boolean = false,
    )

    companion object {
        /** Documented on the result card, since Epley degrades fast past ten reps. */
        const val FORMULA_NAME: String = OneRepMaxCalculator.FORMULA_NAME
        private const val KEY_WEIGHT = "calc_weight"
        private const val KEY_REPS = "calc_reps"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
