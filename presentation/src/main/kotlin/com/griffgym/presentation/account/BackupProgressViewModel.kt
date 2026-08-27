package com.griffgym.presentation.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.account.ContinueLocallyUseCase
import com.griffgym.application.account.UpgradeLocalUserToAccountUseCase
import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.BackupProgress
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
 * The migration: everything already on this phone, uploaded into a brand new account.
 *
 * The upload starts on its own, because the lifter has already agreed to it by creating an
 * account with training data on the device — an extra "start backup" button would only be
 * another place to abandon the flow half-finished.
 *
 * Failure is a first-class state here rather than an error toast. If the upload does not
 * finish, the app is still local-only (see [UpgradeLocalUserToAccountUseCase], which marks
 * the session authenticated only on success), the training data is untouched, and the
 * screen says both of those things out loud before offering to try again.
 */
@HiltViewModel
class BackupProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val upgradeLocalUserToAccount: UpgradeLocalUserToAccountUseCase,
    private val continueLocally: ContinueLocallyUseCase,
) : ViewModel() {

    private val session: AuthSession = savedStateHandle.requireAuthSession()

    private val _uiState = MutableStateFlow(BackupProgressUiState())
    val uiState: StateFlow<BackupProgressUiState> = _uiState.asStateFlow()

    private val completionChannel = Channel<AuthFlowResult>(Channel.BUFFERED)
    internal val completion = completionChannel.receiveAsFlow()

    private var backupJob: Job? = null

    init {
        start()
    }

    fun onEvent(event: BackupProgressUiEvent) {
        when (event) {
            BackupProgressUiEvent.Retry -> start()
            BackupProgressUiEvent.ContinueWithoutBackup -> continueWithoutBackup()
        }
    }

    private fun start() {
        if (backupJob?.isActive == true) return

        _uiState.value = BackupProgressUiState()
        backupJob = viewModelScope.launch {
            upgradeLocalUserToAccount(session, ::onProgress)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            status = TransferStatus.DONE,
                            steps = it.steps.map { step -> step.copy(state = StepState.DONE) },
                            fraction = 1f,
                        )
                    }
                    completionChannel.send(AuthFlowResult.Authenticated(session))
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

    private fun onProgress(progress: BackupProgress) {
        _uiState.update {
            it.copy(
                steps = BackupSteps.at(progress.stage),
                fraction = BackupSteps.fraction(progress.stage, progress.fraction),
            )
        }
    }

    /**
     * The way out of a failed backup that does not pretend anything was backed up.
     *
     * The account exists, but this device's history is not in it, so the app is recorded as
     * local-only — the honest description of where the data lives right now. The Account
     * screen still offers to sign in and back up, and nothing has been lost in the meantime.
     */
    private fun continueWithoutBackup() {
        viewModelScope.launch {
            runCatching { continueLocally() }
            completionChannel.send(AuthFlowResult.ContinuedLocally)
        }
    }
}
