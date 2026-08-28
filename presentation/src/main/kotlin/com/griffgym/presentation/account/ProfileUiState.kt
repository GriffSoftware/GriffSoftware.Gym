package com.griffgym.presentation.account

import androidx.compose.runtime.Immutable

/**
 * How far into deleting an account the lifter has got.
 *
 * Modelled as one position rather than three booleans so the impossible states — a
 * confirmation dialog over an explanation dialog, a failure with no attempt behind it —
 * cannot be represented at all.
 */
enum class DeleteAccountStage {

    /** Nothing is being asked. The default, and where CANCEL always leads back to. */
    NONE,

    /** "This is what will be removed." Says what it costs, asks nothing yet. */
    EXPLANATION,

    /** "Type DELETE to confirm." The only stage from which the API call can be made. */
    CONFIRMATION,

    /** The server said no, and nothing was removed. */
    FAILURE,
}

/**
 * Why the deletion did not happen, in the only two flavours the lifter can act on
 * differently.
 *
 * [SESSION_EXPIRED] is separated out because the remedy is different: no amount of retrying
 * will delete an account the app can no longer prove it owns.
 */
enum class DeleteAccountFailureUi {

    /** No connection, a 500, a timeout. Worth trying again. */
    RETRYABLE,

    /** The access token was rejected and the refresh failed with it. Sign in first. */
    SESSION_EXPIRED,
}

@Immutable
data class DeleteAccountUiState(
    val stage: DeleteAccountStage = DeleteAccountStage.NONE,
    /** Exactly what the lifter typed. Compared trimmed, never coerced. */
    val confirmationInput: String = "",
    val isDeleting: Boolean = false,
    val failure: DeleteAccountFailureUi? = null,
) {
    /**
     * The confirmation phrase is matched **case-sensitively** on a trimmed input.
     *
     * Surrounding whitespace is forgiven because a soft keyboard adds it and it carries no
     * intent; the casing is not, because typing it in capitals is the deliberate act the
     * second stage exists to require. The field asks for capitals
     * ([androidx.compose.ui.text.input.KeyboardCapitalization.Characters]) so this costs a
     * lifter who means it nothing.
     */
    val isConfirmationPhraseTyped: Boolean
        get() = confirmationInput.trim() == CONFIRMATION_PHRASE

    /** Enabled only for a typed confirmation that is not already being acted on. */
    val canConfirmDeletion: Boolean
        get() = isConfirmationPhraseTyped && !isDeleting

    companion object {
        const val CONFIRMATION_PHRASE = "DELETE"
    }
}

@Immutable
data class ProfileUiState(
    val isLoading: Boolean = true,
    /**
     * The address the session was minted for, and the only profile field there is. Griff Gym
     * asks for nothing else, so the screen invents nothing else.
     */
    val email: String? = null,
    val status: BackupStatusUi = BackupStatusUi.NOT_BACKED_UP,
    /** "Today, 18:42", or null when no sync has ever finished on this device. */
    val lastBackupLabel: String? = null,
    val isSyncing: Boolean = false,
    val isConfirmingSignOut: Boolean = false,
    val deletion: DeleteAccountUiState = DeleteAccountUiState(),
    /** Non-technical, transient. Sync and sign-out failures land here, deletion does not. */
    val message: String? = null,
)

sealed interface ProfileUiEvent {

    data object SyncNow : ProfileUiEvent

    data object SignOutRequested : ProfileUiEvent
    data object ConfirmSignOut : ProfileUiEvent
    data object DismissSignOut : ProfileUiEvent

    /** Opens the first of the two questions. Never deletes anything by itself. */
    data object DeleteAccountRequested : ProfileUiEvent

    /** CONTINUE on the first dialog: the lifter has read what will be removed. */
    data object DeleteAccountExplained : ProfileUiEvent

    data class DeleteConfirmationChanged(val value: String) : ProfileUiEvent

    data object ConfirmDeleteAccount : ProfileUiEvent

    /** After a failure. The account still exists, so this is an ordinary second attempt. */
    data object RetryDeleteAccount : ProfileUiEvent

    /** CANCEL, or system back. Closes whichever dialog is open and deletes nothing. */
    data object DismissDeleteAccount : ProfileUiEvent

    data object MessageShown : ProfileUiEvent
}

/**
 * The two ways this screen ends, both of which tear the whole app graph down.
 *
 * One-shot events rather than state: they are consumed once by the host, and a state flag
 * would replay them on every configuration change.
 */
sealed interface ProfileNavigationEvent {

    /** The session is gone; the cloud copy is not. Back to the entry screen.  */
    data object SignedOut : ProfileNavigationEvent

    /** The account is gone, along with Room and the setup flag. Back to first run. */
    data object AccountDeleted : ProfileNavigationEvent
}
