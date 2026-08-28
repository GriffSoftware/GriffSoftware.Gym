package com.griffgym.presentation.account

import androidx.compose.runtime.Immutable

@Immutable
data class DataProtectionUiState(
    /**
     * The one confirmation between "continue locally" and living without a backup. One,
     * not a chain: a second are-you-sure teaches people to tap through warnings.
     */
    val isConfirmingLocalOnly: Boolean = false,
    val isWorking: Boolean = false,
    /**
     * Kept apart from [isWorking] because it is the only one with a visible label to change:
     * the Google button says what it is doing while the account picker is up.
     */
    val isSigningInWithGoogle: Boolean = false,
    /**
     * A Google sign-in that did not work out. The local-only path has no failure worth
     * showing — see [DataProtectionViewModel] — so this is the screen's only error.
     */
    val formError: String? = null,
) {
    /** Nothing else may be started while either path is in flight. */
    val isBusy: Boolean get() = isWorking || isSigningInWithGoogle
}

sealed interface DataProtectionUiEvent {
    data object ContinueLocallyRequested : DataProtectionUiEvent
    data object ConfirmContinueLocally : DataProtectionUiEvent
    data object DismissConfirmation : DataProtectionUiEvent
}
