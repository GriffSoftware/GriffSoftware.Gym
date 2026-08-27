package com.griffgym.presentation.account

import androidx.compose.runtime.Immutable
import com.griffgym.domain.model.CloudSyncState

/**
 * The two ways the app can be used, as the UI needs to branch on them.
 *
 * [com.griffgym.domain.model.UserMode.Undecided] is folded into [LOCAL_ONLY] on purpose:
 * a lifter who has not answered the entry screen yet is, right now, storing everything on
 * this phone, and the account screen should tell them that rather than show a third state
 * with nothing useful in it.
 */
enum class AccountMode {
    LOCAL_ONLY,
    AUTHENTICATED,
}

/**
 * The one-line answer to "is my training safe?".
 *
 * [label] is what appears on the badge; it is the whole message for most lifters, most of
 * the time.
 */
enum class BackupStatusUi(val label: String) {
    NOT_BACKED_UP("NOT BACKED UP"),
    BACKED_UP("BACKED UP"),
    BACKING_UP("BACKING UP…"),
    PENDING("BACKUP PENDING"),
    OFFLINE("OFFLINE"),
    FAILED("BACKUP FAILED"),

    /** Two versions of the same record. Nothing was overwritten, so nothing is urgent. */
    NEEDS_ATTENTION("NEEDS ATTENTION");

    val isReassuring: Boolean get() = this == BACKED_UP

    companion object {
        fun from(state: CloudSyncState): BackupStatusUi = when (state) {
            CloudSyncState.LOCAL_ONLY -> NOT_BACKED_UP
            CloudSyncState.SYNCED -> BACKED_UP
            CloudSyncState.SYNCING -> BACKING_UP
            CloudSyncState.PENDING -> PENDING
            CloudSyncState.OFFLINE -> OFFLINE
            CloudSyncState.ERROR -> FAILED
            CloudSyncState.CONFLICT -> NEEDS_ATTENTION
        }
    }
}

@Immutable
data class AccountUiState(
    val isLoading: Boolean = true,
    val mode: AccountMode = AccountMode.LOCAL_ONLY,
    val email: String? = null,
    val status: BackupStatusUi = BackupStatusUi.NOT_BACKED_UP,
    /**
     * "Today, 18:42", or null when nothing has ever finished syncing on this device.
     *
     * Absent rather than "never": a fresh account that has not had its first sync land yet
     * is not the same as a broken one, and a row that says "Last sync — never" reads like
     * a fault.
     */
    val lastSyncLabel: String? = null,
    val isSyncing: Boolean = false,
    val isConfirmingSignOut: Boolean = false,
    /** Non-technical, transient. Cleared once the lifter has seen it. */
    val message: String? = null,
) {
    val isOffline: Boolean get() = status == BackupStatusUi.OFFLINE
}

sealed interface AccountUiEvent {
    data object SyncNow : AccountUiEvent
    data object SignOutRequested : AccountUiEvent
    data object ConfirmSignOut : AccountUiEvent
    data object DismissSignOut : AccountUiEvent
    data object MessageShown : AccountUiEvent
}
