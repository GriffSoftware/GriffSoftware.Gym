package com.griffgym.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.account.RegisterUseCase
import com.griffgym.domain.model.AuthSession
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
 * Creating an account, and working out what that means for the training data already on
 * the phone.
 *
 * Registration itself is one call. The interesting half is what follows it: the app is
 * *not* authenticated the moment the server says yes — see [PostSignInRouter] — because a
 * lifter with six months of local history has not been backed up until those six months
 * have actually been uploaded.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val register: RegisterUseCase,
    private val postSignInRouter: PostSignInRouter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val stepChannel = Channel<AuthFlowStep>(Channel.BUFFERED)
    internal val steps = stepChannel.receiveAsFlow()

    /** A second tap must not create a second account, or race the first one's response. */
    private var submitJob: Job? = null

    fun onEvent(event: RegisterUiEvent) {
        when (event) {
            is RegisterUiEvent.EmailChanged -> _uiState.update {
                it.copy(email = event.value).clearing(AuthField.EMAIL)
            }

            is RegisterUiEvent.PasswordChanged -> _uiState.update {
                it.copy(password = event.value)
                    .clearing(AuthField.PASSWORD)
                    // Editing the password makes any earlier mismatch meaningless — the
                    // confirmation is now being compared against something else.
                    .clearing(AuthField.CONFIRM_PASSWORD)
            }

            is RegisterUiEvent.ConfirmPasswordChanged -> _uiState.update {
                it.copy(confirmPassword = event.value).clearing(AuthField.CONFIRM_PASSWORD)
            }

            RegisterUiEvent.TogglePasswordVisibility -> _uiState.update {
                it.copy(isPasswordVisible = !it.isPasswordVisible)
            }

            RegisterUiEvent.Submit -> submit()
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
            register(state.email, state.password)
                .onSuccess { session -> resolve(session) }
                .onFailure { error -> fail(error.toAuthFailure(AuthContext.REGISTER)) }
        }
    }

    private suspend fun resolve(session: AuthSession) {
        postSignInRouter.route(session)
            .onSuccess { step ->
                // The account exists and the flow is moving on, so the credentials have no
                // further use here. Nothing keeps them alive in a state that could end up
                // in a saved-state bundle or a heap dump.
                _uiState.update {
                    it.copy(isSubmitting = false, password = "", confirmPassword = "")
                }
                stepChannel.send(step)
            }
            .onFailure {
                // The account was created but its state could not be read, so nothing has
                // been marked as backed up and the local data is untouched. Say so, and
                // point at the sign-in link that is already on this screen.
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        formError = AccountMessages.REGISTERED_BUT_UNRESOLVED,
                    )
                }
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

    private fun validate(state: RegisterUiState): Map<AuthField, String> = buildMap {
        Credentials.emailError(state.email)?.let { put(AuthField.EMAIL, it) }
        Credentials.passwordError(state.password)?.let { put(AuthField.PASSWORD, it) }
        Credentials.confirmationError(state.password, state.confirmPassword)
            ?.let { put(AuthField.CONFIRM_PASSWORD, it) }
    }

    /**
     * An error stops applying the moment its input changes — leaving "passwords do not
     * match" under a field the lifter is actively fixing is how a form feels broken.
     */
    private fun RegisterUiState.clearing(field: AuthField): RegisterUiState =
        if (fieldErrors.containsKey(field)) copy(fieldErrors = fieldErrors - field) else this
}
