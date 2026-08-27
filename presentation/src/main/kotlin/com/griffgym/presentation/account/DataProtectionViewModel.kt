package com.griffgym.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.account.ContinueLocallyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The first fork: an account, or this phone and nothing else.
 *
 * The screen itself holds no logic beyond which of the two the lifter tapped. What this
 * ViewModel owns is the one thing that must survive a rotation mid-decision — whether the
 * confirmation is open — and the single write that records the answer.
 */
@HiltViewModel
class DataProtectionViewModel @Inject constructor(
    private val continueLocally: ContinueLocallyUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataProtectionUiState())
    val uiState: StateFlow<DataProtectionUiState> = _uiState.asStateFlow()

    private val completionChannel = Channel<AuthFlowResult>(Channel.BUFFERED)

    /** Emits exactly once, when the local-only choice has been recorded. */
    internal val completion = completionChannel.receiveAsFlow()

    /** Held so a double tap on the confirmation cannot write the choice twice. */
    private var choiceJob: Job? = null

    fun onEvent(event: DataProtectionUiEvent) {
        when (event) {
            DataProtectionUiEvent.ContinueLocallyRequested ->
                _uiState.update { it.copy(isConfirmingLocalOnly = true) }

            DataProtectionUiEvent.DismissConfirmation ->
                _uiState.update { it.copy(isConfirmingLocalOnly = false) }

            DataProtectionUiEvent.ConfirmContinueLocally -> chooseLocalOnly()
        }
    }

    private fun chooseLocalOnly() {
        if (choiceJob?.isActive == true) return

        _uiState.update { it.copy(isWorking = true) }
        choiceJob = viewModelScope.launch {
            // The choice is a preference, not the lifter's data. If writing it fails the
            // worst case is being asked again on the next launch, which is a far better
            // outcome than refusing to let somebody into an app that works entirely
            // offline. Nothing is lost either way, so the flow continues regardless.
            runCatching { continueLocally() }

            _uiState.update { it.copy(isConfirmingLocalOnly = false, isWorking = false) }
            completionChannel.send(AuthFlowResult.ContinuedLocally)
        }
    }
}
