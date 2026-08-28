package com.griffgym.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.account.DeleteAccountUseCase
import com.griffgym.application.account.GetUserModeUseCase
import com.griffgym.application.account.LogoutUseCase
import com.griffgym.application.sync.GetCloudSyncStatusUseCase
import com.griffgym.application.sync.SyncNowUseCase
import com.griffgym.domain.model.CloudSyncState
import com.griffgym.domain.model.CloudSyncStatus
import com.griffgym.domain.model.GriffGymError
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
 * The signed-in lifter's own screen: who they are, whether their training is backed up, and
 * the two ways to leave.
 *
 * Sign-out reuses [LogoutUseCase] unchanged, so it behaves here exactly as it does on the
 * account screen — one path, one set of guarantees, no second implementation to drift.
 *
 * Deletion is the part that needs care, and everything below is arranged around one rule:
 * **nothing local changes until the server has confirmed.** [DeleteAccountUseCase] enforces
 * that; this class's job is to make sure the lifter has said so twice, that a second tap
 * cannot start a second deletion, and that a failure leaves them exactly where they were —
 * still signed in, still on this screen, with their training untouched.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    getUserMode: GetUserModeUseCase,
    getCloudSyncStatus: GetCloudSyncStatusUseCase,
    private val syncNow: SyncNowUseCase,
    private val logout: LogoutUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val localState = MutableStateFlow(LocalState())

    private val navigationChannel = Channel<ProfileNavigationEvent>(Channel.BUFFERED)

    /** Consumed once by the host, which then swaps the whole subtree out. */
    val navigation = navigationChannel.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState> = combine(
        getUserMode(),
        getCloudSyncStatus(),
        localState,
    ) { userMode, syncStatus, local ->
        userMode.toUiState(syncStatus, local)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ProfileUiState(),
    )

    private var syncJob: Job? = null
    private var signOutJob: Job? = null

    /**
     * Held so a second ConfirmDeleteAccount — a double tap, or TRY AGAIN pressed twice —
     * finds the first one still running and does nothing. A boolean in [localState] would
     * work too, but a job is what actually answers "is one in flight?" without a window
     * between the launch and the state update.
     */
    private var deleteJob: Job? = null

    /**
     * Latched once the server has confirmed. [deleteJob] alone is not enough: a fast
     * deletion completes before the second half of a double tap arrives, and a second call
     * would then be made against an account that no longer exists.
     */
    private var isAccountDeleted: Boolean = false

    fun onEvent(event: ProfileUiEvent) {
        when (event) {
            ProfileUiEvent.SyncNow -> sync()

            ProfileUiEvent.SignOutRequested ->
                localState.update { it.copy(isConfirmingSignOut = true) }

            ProfileUiEvent.DismissSignOut ->
                localState.update { it.copy(isConfirmingSignOut = false) }

            ProfileUiEvent.ConfirmSignOut -> signOut()

            ProfileUiEvent.DeleteAccountRequested -> updateDeletion {
                DeleteAccountUiState(stage = DeleteAccountStage.EXPLANATION)
            }

            // A fresh, empty field every time the second question is reached: a phrase left
            // over from an abandoned attempt would arm the confirm button before the lifter
            // has typed anything.
            ProfileUiEvent.DeleteAccountExplained -> updateDeletion {
                DeleteAccountUiState(stage = DeleteAccountStage.CONFIRMATION)
            }

            is ProfileUiEvent.DeleteConfirmationChanged -> updateDeletion {
                it.copy(confirmationInput = event.value)
            }

            ProfileUiEvent.ConfirmDeleteAccount -> confirmDeletion()

            ProfileUiEvent.RetryDeleteAccount -> retryDeletion()

            ProfileUiEvent.DismissDeleteAccount -> dismissDeletion()

            ProfileUiEvent.MessageShown -> localState.update { it.copy(message = null) }
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
                    navigationChannel.send(ProfileNavigationEvent.SignedOut)
                }
                .onFailure { error ->
                    // Sign-out is written to survive a dead network, so a failure here means
                    // something local went wrong and the session is still live. Say so.
                    localState.update {
                        it.copy(
                            isConfirmingSignOut = false,
                            message = error.toAuthFailure(AuthContext.ACCOUNT).message,
                        )
                    }
                }
        }
    }

    /** The only entry point that may start a deletion, and only from the second stage. */
    private fun confirmDeletion() {
        if (!localState.value.deletion.canConfirmDeletion) return
        runDeletion()
    }

    /**
     * TRY AGAIN returns to the confirmation dialog rather than firing straight from the
     * failure one, so the lifter sees DELETING ACCOUNT… in the place they expect it and the
     * phrase they typed is still visible above it.
     */
    private fun retryDeletion() {
        if (localState.value.deletion.stage != DeleteAccountStage.FAILURE) return

        updateDeletion { it.copy(stage = DeleteAccountStage.CONFIRMATION, failure = null) }
        runDeletion()
    }

    private fun runDeletion() {
        if (isAccountDeleted || deleteJob?.isActive == true) return

        updateDeletion { it.copy(isDeleting = true, failure = null) }
        deleteJob = viewModelScope.launch {
            deleteAccount()
                .onSuccess {
                    // isDeleting is deliberately left set. The account is gone and the host
                    // is about to tear this whole graph down; re-arming the confirm button
                    // for the frame in between would be offering an action that cannot work.
                    isAccountDeleted = true
                    navigationChannel.send(ProfileNavigationEvent.AccountDeleted)
                }
                .onFailure { error ->
                    // Nothing was removed — not the account, not the tokens, not a row of
                    // training. The screen stays exactly as it was and says so; substituting
                    // a local wipe or a sign-out here would destroy data the lifter still has
                    // an account for.
                    updateDeletion {
                        it.copy(
                            stage = DeleteAccountStage.FAILURE,
                            isDeleting = false,
                            failure = error.toDeleteAccountFailure(),
                        )
                    }
                }
        }
    }

    /**
     * Ignored while a deletion is in flight. The call is already at the server and cannot be
     * recalled, so closing the dialog would only hide an operation that is still going to
     * finish — and the lifter would be looking at a Profile screen for an account that is
     * about to stop existing.
     */
    private fun dismissDeletion() {
        if (isAccountDeleted || deleteJob?.isActive == true) return
        updateDeletion { DeleteAccountUiState() }
    }

    private fun updateDeletion(transform: (DeleteAccountUiState) -> DeleteAccountUiState) {
        localState.update { it.copy(deletion = transform(it.deletion)) }
    }

    private fun UserMode.toUiState(
        syncStatus: CloudSyncStatus,
        local: LocalState,
    ): ProfileUiState {
        val authenticated = this as? UserMode.Authenticated
        return ProfileUiState(
            isLoading = false,
            email = authenticated?.email,
            // Without an account there is nothing to be pending or offline about, whatever a
            // stale status flow happens to be holding.
            status = if (authenticated == null) {
                BackupStatusUi.NOT_BACKED_UP
            } else {
                BackupStatusUi.from(syncStatus.state)
            },
            lastBackupLabel = authenticated?.let {
                syncStatus.lastSyncedAt?.let { at -> AccountFormat.lastSync(at, clock) }
            },
            isSyncing = local.isSyncing || syncStatus.state == CloudSyncState.SYNCING,
            isConfirmingSignOut = local.isConfirmingSignOut,
            deletion = local.deletion,
            message = local.message,
        )
    }

    private data class LocalState(
        val isSyncing: Boolean = false,
        val isConfirmingSignOut: Boolean = false,
        val deletion: DeleteAccountUiState = DeleteAccountUiState(),
        val message: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Kept apart from [toAuthFailure] because deletion asks a different question of the same
 * errors: not "what went wrong" but "is trying again going to help".
 */
private fun Throwable.toDeleteAccountFailure(): DeleteAccountFailureUi = when (this) {
    // The access token was rejected and TokenAuthenticator's refresh failed with it. The
    // account is still there; the app simply can no longer prove it owns it.
    is GriffGymError.Unauthorized -> DeleteAccountFailureUi.SESSION_EXPIRED
    else -> DeleteAccountFailureUi.RETRYABLE
}
