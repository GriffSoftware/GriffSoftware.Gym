package com.griffgym.presentation.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.account.InitializeAuthenticatedSessionUseCase
import com.griffgym.application.sync.RestoreCloudStateUseCase
import com.griffgym.domain.model.AuthSession
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
 * Pulling an account's training history down onto this device.
 *
 * Reports no stages, unlike the backup, because the restore genuinely has none to report:
 * it is one Room transaction, all of it or none of it (see [RestoreCloudStateUseCase]).
 * Inventing "downloading cycles… downloading workouts…" for a screen that cannot observe
 * either would be a progress bar made of guesses.
 *
 * The session is only marked authenticated once the data has actually landed. A restore
 * that fails leaves the database exactly as it was and the app not claiming an account,
 * which is what makes the retry safe to press as many times as it takes.
 */
@HiltViewModel
class RestoreProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val restoreCloudState: RestoreCloudStateUseCase,
    private val initializeAuthenticatedSession: InitializeAuthenticatedSessionUseCase,
) : ViewModel() {

    private val session: AuthSession = savedStateHandle.requireAuthSession()

    private val _uiState = MutableStateFlow(RestoreProgressUiState())
    val uiState: StateFlow<RestoreProgressUiState> = _uiState.asStateFlow()

    private val completionChannel = Channel<AuthFlowResult>(Channel.BUFFERED)
    internal val completion = completionChannel.receiveAsFlow()

    private var restoreJob: Job? = null

    init {
        start()
    }

    fun onEvent(event: RestoreProgressUiEvent) {
        when (event) {
            RestoreProgressUiEvent.Retry -> start()
        }
    }

    private fun start() {
        if (restoreJob?.isActive == true) return

        _uiState.value = RestoreProgressUiState()
        restoreJob = viewModelScope.launch {
            restoreCloudState()
                .mapCatching { initializeAuthenticatedSession(session) }
                .onSuccess {
                    _uiState.update { it.copy(status = TransferStatus.DONE) }
                    completionChannel.send(AuthFlowResult.Restored(session))
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            status = TransferStatus.FAILED,
                            error = error.toAuthFailure(AuthContext.ACCOUNT).message
                                ?: AccountMessages.GENERIC,
                        )
                    }
                }
        }
    }
}
