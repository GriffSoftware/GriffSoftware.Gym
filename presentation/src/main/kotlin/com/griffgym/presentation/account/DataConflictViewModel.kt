package com.griffgym.presentation.account

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@Immutable
data class DataConflictUiState(
    /**
     * The second, explicit confirmation in front of replacing this device's history.
     *
     * Held here rather than in the composable so that a rotation — or a phone call — while
     * the lifter is thinking about it does not silently close the question.
     */
    val isConfirmingUseCloud: Boolean = false,
)

sealed interface DataConflictUiEvent {
    data object UseCloudRequested : DataConflictUiEvent
    data object ConfirmUseCloud : DataConflictUiEvent
    data object DismissConfirmation : DataConflictUiEvent
}

/**
 * The refusal screen's state, and nothing else.
 *
 * There is deliberately no use case behind this ViewModel: the entire point of the conflict
 * screen is that the app does **not** act. It has two histories, no way to tell whether
 * they are the same one, and no mandate to merge or overwrite either. All it owns is
 * whether the lifter has been asked the second question yet — the actual restore is the
 * existing, transactional [RestoreProgressViewModel], reached only after they answer it.
 */
@HiltViewModel
class DataConflictViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DataConflictUiState())
    val uiState: StateFlow<DataConflictUiState> = _uiState.asStateFlow()

    fun onEvent(event: DataConflictUiEvent) {
        when (event) {
            DataConflictUiEvent.UseCloudRequested ->
                _uiState.update { it.copy(isConfirmingUseCloud = true) }

            DataConflictUiEvent.DismissConfirmation, DataConflictUiEvent.ConfirmUseCloud ->
                _uiState.update { it.copy(isConfirmingUseCloud = false) }
        }
    }
}
