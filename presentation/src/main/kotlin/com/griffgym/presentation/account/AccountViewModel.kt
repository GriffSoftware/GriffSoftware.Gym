package com.griffgym.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.account.GetUserModeUseCase
import com.griffgym.application.account.LogoutUseCase
import com.griffgym.application.sync.GetCloudSyncStatusUseCase
import com.griffgym.application.sync.SyncNowUseCase
import com.griffgym.domain.model.CloudSyncState
import com.griffgym.domain.model.CloudSyncStatus
import com.griffgym.domain.model.UserMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * Where the lifter's training data lives, and the two buttons that change it.
 *
 * Both the mode and the sync status are observed rather than read once: a background sync
 * finishing while this screen is open should move the badge to BACKED UP without anybody
 * pulling to refresh, and a session that expires elsewhere should not leave the screen
 * claiming an account.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    getUserMode: GetUserModeUseCase,
    getCloudSyncStatus: GetCloudSyncStatusUseCase,
    private val syncNow: SyncNowUseCase,
    private val logout: LogoutUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val localState = MutableStateFlow(LocalState())

    private val signedOutChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits once the session and this account's cached data are gone from the device. */
    val signedOut = signedOutChannel.receiveAsFlow()

    val uiState: StateFlow<AccountUiState> = combine(
        getUserMode(),
        getCloudSyncStatus(),
        localState,
    ) { userMode, syncStatus, local ->
        userMode.toUiState(syncStatus, local)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AccountUiState(),
    )

    private var syncJob: Job? = null
    private var signOutJob: Job? = null

    fun onEvent(event: AccountUiEvent) {
        when (event) {
            AccountUiEvent.SyncNow -> sync()
            AccountUiEvent.SignOutRequested ->
                localState.update { it.copy(isConfirmingSignOut = true) }

            AccountUiEvent.DismissSignOut ->
                localState.update { it.copy(isConfirmingSignOut = false) }

            AccountUiEvent.ConfirmSignOut -> signOut()
            AccountUiEvent.MessageShown -> localState.update { it.copy(message = null) }
        }
    }

    private fun sync() {
        if (syncJob?.isActive == true) return

        localState.update { it.copy(isSyncing = true, message = null) }
        syncJob = viewModelScope.launch {
            val message = syncNow().fold(
                onSuccess = { null },
                onFailure = { it.toAuthFailure(AuthContext.ACCOUNT).message },
            )
            localState.update { it.copy(isSyncing = false, message = message) }
        }
    }

    private fun signOut() {
        if (signOutJob?.isActive == true) return

        signOutJob = viewModelScope.launch {
            logout()
                .onSuccess {
                    localState.update { it.copy(isConfirmingSignOut = false) }
                    signedOutChannel.send(Unit)
                }
                .onFailure { error ->
                    // Sign-out is written to survive a dead network, so a failure here means
                    // something local went wrong. The session is still live; say so rather
                    // than leaving the screen in a state that implies it is not.
                    localState.update {
                        it.copy(
                            isConfirmingSignOut = false,
                            message = error.toAuthFailure(AuthContext.ACCOUNT).message,
                        )
                    }
                }
        }
    }

    private fun UserMode.toUiState(
        syncStatus: CloudSyncStatus,
        local: LocalState,
    ): AccountUiState {
        val authenticated = this as? UserMode.Authenticated
        return AccountUiState(
            isLoading = false,
            mode = if (authenticated != null) {
                AccountMode.AUTHENTICATED
            } else {
                AccountMode.LOCAL_ONLY
            },
            email = authenticated?.email,
            status = if (authenticated == null) {
                // Without an account there is nothing to be pending or offline about,
                // whatever a stale status flow happens to be holding.
                BackupStatusUi.NOT_BACKED_UP
            } else {
                BackupStatusUi.from(syncStatus.state)
            },
            lastSyncLabel = authenticated?.let {
                syncStatus.lastSyncedAt?.let { at -> AccountFormat.lastSync(at, clock) }
            },
            isSyncing = local.isSyncing || syncStatus.state == CloudSyncState.SYNCING,
            isConfirmingSignOut = local.isConfirmingSignOut,
            message = local.message,
        )
    }

    private data class LocalState(
        val isSyncing: Boolean = false,
        val isConfirmingSignOut: Boolean = false,
        val message: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
