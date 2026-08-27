package com.griffgym.presentation.account

import androidx.compose.runtime.Immutable

@Immutable
data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    /**
     * One toggle for both password fields. Two independent eyes on a form where the whole
     * point is comparing the values would be a strange thing to build.
     */
    val isPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val fieldErrors: Map<AuthField, String> = emptyMap(),
    val formError: String? = null,
) {
    /**
     * Only "not already busy" — deliberately not "everything is valid".
     *
     * A button that greys itself out until a hidden set of rules is satisfied leaves the
     * lifter to guess which rule they are failing. Tapping it and being told is faster.
     */
    val canSubmit: Boolean get() = !isSubmitting

    fun errorFor(field: AuthField): String? = fieldErrors[field]
}

sealed interface RegisterUiEvent {
    data class EmailChanged(val value: String) : RegisterUiEvent
    data class PasswordChanged(val value: String) : RegisterUiEvent
    data class ConfirmPasswordChanged(val value: String) : RegisterUiEvent
    data object TogglePasswordVisibility : RegisterUiEvent
    data object Submit : RegisterUiEvent
}
