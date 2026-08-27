package com.griffgym.presentation.account

import androidx.compose.runtime.Immutable

@Immutable
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val fieldErrors: Map<AuthField, String> = emptyMap(),
    val formError: String? = null,
    /** The same single confirmation the entry screen uses before going local-only. */
    val isConfirmingLocalOnly: Boolean = false,
) {
    val canSubmit: Boolean get() = !isSubmitting

    fun errorFor(field: AuthField): String? = fieldErrors[field]
}

sealed interface LoginUiEvent {
    data class EmailChanged(val value: String) : LoginUiEvent
    data class PasswordChanged(val value: String) : LoginUiEvent
    data object TogglePasswordVisibility : LoginUiEvent
    data object Submit : LoginUiEvent
    data object ContinueLocallyRequested : LoginUiEvent
    data object ConfirmContinueLocally : LoginUiEvent
    data object DismissConfirmation : LoginUiEvent
}
