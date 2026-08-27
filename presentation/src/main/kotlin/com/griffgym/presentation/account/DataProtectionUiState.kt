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
)

sealed interface DataProtectionUiEvent {
    data object ContinueLocallyRequested : DataProtectionUiEvent
    data object ConfirmContinueLocally : DataProtectionUiEvent
    data object DismissConfirmation : DataProtectionUiEvent
}
