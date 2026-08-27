package com.griffgym.presentation.account

import androidx.compose.runtime.Immutable

@Immutable
data class RestoreProgressUiState(
    val status: TransferStatus = TransferStatus.RUNNING,
    /** Plain, non-technical. Null unless [status] is [TransferStatus.FAILED]. */
    val error: String? = null,
) {
    val isRunning: Boolean get() = status == TransferStatus.RUNNING
}

sealed interface RestoreProgressUiEvent {
    data object Retry : RestoreProgressUiEvent
}
