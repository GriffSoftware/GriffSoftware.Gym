package com.griffgym.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.account.ContinueLocallyUseCase
import com.griffgym.application.account.LoginUseCase
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
 * Signing in, on a new phone or after a reinstall.
 *
 * Offers the local-only escape hatch as well, because somebody who opened this screen and
 * cannot remember their password must not be trapped in the auth flow with a gym session
 * to log. That decision goes through the same confirmation as the entry screen — the copy
 * lives in one place, and the warning is shown exactly once wherever it is reached from.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
    private val continueLocally: ContinueLocallyUseCase,
    private val postSignInRouter: PostSignInRouter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val stepChannel = Channel<AuthFlowStep>(Channel.BUFFERED)
    internal val steps = stepChannel.receiveAsFlow()

    private var submitJob: Job? = null
    private var localOnlyJob: Job? = null

    fun onEvent(event: LoginUiEvent) {
        when (event) {
            is LoginUiEvent.EmailChanged -> _uiState.update {
                it.copy(email = event.value).clearing(AuthField.EMAIL)
            }

            is LoginUiEvent.PasswordChanged -> _uiState.update {
                it.copy(password = event.value).clearing(AuthField.PASSWORD)
            }

            LoginUiEvent.TogglePasswordVisibility -> _uiState.update {
                it.copy(isPasswordVisible = !it.isPasswordVisible)
            }

            LoginUiEvent.Submit -> submit()

            LoginUiEvent.ContinueLocallyRequested ->
                _uiState.update { it.copy(isConfirmingLocalOnly = true) }

            LoginUiEvent.DismissConfirmation ->
                _uiState.update { it.copy(isConfirmingLocalOnly = false) }

            LoginUiEvent.ConfirmContinueLocally -> chooseLocalOnly()
        }
    }

    private fun submit() {
        if (submitJob?.isActive == true) return

        val state = _uiState.value
        val localErrors = validate(state)
        if (localErrors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = localErrors, formError = null) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, fieldErrors = emptyMap(), formError = null) }
        submitJob = viewModelScope.launch {
            login(state.email, state.password)
                .onSuccess { session ->
                    postSignInRouter.route(session)
                        .onSuccess { step ->
                            _uiState.update { it.copy(isSubmitting = false, password = "") }
                            stepChannel.send(step)
                        }
                        .onFailure { error -> fail(error.toAuthFailure(AuthContext.LOGIN)) }
                }
                .onFailure { error -> fail(error.toAuthFailure(AuthContext.LOGIN)) }
        }
    }

    private fun chooseLocalOnly() {
        if (localOnlyJob?.isActive == true) return

        localOnlyJob = viewModelScope.launch {
            // See DataProtectionViewModel: the choice is a preference, and failing to store
            // it costs nothing more than being asked again next launch.
            runCatching { continueLocally() }

            _uiState.update { it.copy(isConfirmingLocalOnly = false) }
            stepChannel.send(AuthFlowStep.Finish(AuthFlowResult.ContinuedLocally))
        }
    }

    private fun fail(failure: AuthFailure) {
        _uiState.update {
            it.copy(
                isSubmitting = false,
                fieldErrors = failure.fieldErrors,
                formError = failure.message,
            )
        }
    }

    /**
     * Sign-in checks only that there is something to send. A password that no longer meets
     * today's rules is still the password on an existing account, and refusing to try it
     * would lock somebody out of their own training history over a client-side opinion.
     */
    private fun validate(state: LoginUiState): Map<AuthField, String> = buildMap {
        Credentials.emailError(state.email)?.let { put(AuthField.EMAIL, it) }
        if (state.password.isEmpty()) put(AuthField.PASSWORD, "Enter your password")
    }

    private fun LoginUiState.clearing(field: AuthField): LoginUiState =
        if (fieldErrors.containsKey(field)) copy(fieldErrors = fieldErrors - field) else this
}
