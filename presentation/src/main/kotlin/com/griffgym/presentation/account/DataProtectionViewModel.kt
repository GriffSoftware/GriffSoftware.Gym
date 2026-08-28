package com.griffgym.presentation.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.account.ContinueLocallyUseCase
import com.griffgym.application.account.GoogleLoginUseCase
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
 * The first fork: an account, or this phone and nothing else.
 *
 * Signing in with Google is the third way out, and the only one this screen finishes itself.
 * It can, because Google has already answered the question the two forms exist to ask — the
 * token names a verified address, so registering and signing in are the same request — and
 * the account picker is the whole of the interaction.
 *
 * What happens to the training data afterwards is not decided here. Like [LoginViewModel]
 * and [RegisterViewModel], a session goes through [PostSignInRouter], which is what keeps a
 * lifter's six months of local history from being overwritten by an empty account.
 */
@HiltViewModel
class DataProtectionViewModel @Inject constructor(
    private val continueLocally: ContinueLocallyUseCase,
    private val googleLogin: GoogleLoginUseCase,
    private val googleSignInLauncher: GoogleSignInLauncher,
    private val postSignInRouter: PostSignInRouter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataProtectionUiState())
    val uiState: StateFlow<DataProtectionUiState> = _uiState.asStateFlow()

    private val stepChannel = Channel<AuthFlowStep>(Channel.BUFFERED)

    /**
     * Emits exactly once, when this screen is done with: the local-only choice recorded, or
     * a Google session routed to whatever has to happen to the data next.
     */
    internal val steps = stepChannel.receiveAsFlow()

    /** Held so a double tap on the confirmation cannot write the choice twice. */
    private var choiceJob: Job? = null

    /** Same again for the account picker, which is slow enough to invite a second tap. */
    private var googleJob: Job? = null

    fun onEvent(event: DataProtectionUiEvent) {
        when (event) {
            DataProtectionUiEvent.ContinueLocallyRequested ->
                _uiState.update { it.copy(isConfirmingLocalOnly = true) }

            DataProtectionUiEvent.DismissConfirmation ->
                _uiState.update { it.copy(isConfirmingLocalOnly = false) }

            DataProtectionUiEvent.ConfirmContinueLocally -> chooseLocalOnly()
        }
    }

    /**
     * Not a [DataProtectionUiEvent], because it needs the Activity Credential Manager will
     * put its account picker over.
     *
     * The [context] is passed in at the moment of the tap and never stored: a ViewModel
     * outlives the Activity that created it, so an Activity kept in a field here is a leaked
     * window. Read it from `LocalContext.current` at the call site.
     */
    fun signInWithGoogle(context: Context) {
        if (googleJob?.isActive == true) return

        _uiState.update { it.copy(isSigningInWithGoogle = true, formError = null) }
        googleJob = viewModelScope.launch {
            googleSignInLauncher.requestIdToken(context)
                .onSuccess { idToken -> exchange(idToken) }
                .onFailure { error ->
                    // A dismissed picker is a decision, not a failure. The screen goes back
                    // to how it was and says nothing: an error banner for "I changed my
                    // mind" is how an app teaches people to ignore its error banners.
                    if (error is GoogleSignInException.Cancelled) {
                        _uiState.update { it.copy(isSigningInWithGoogle = false) }
                    } else {
                        fail(error.toGoogleSignInFailure())
                    }
                }
        }
    }

    private suspend fun exchange(idToken: String) {
        googleLogin(idToken)
            .onSuccess { session ->
                postSignInRouter.route(session)
                    .onSuccess { step ->
                        _uiState.update { it.copy(isSigningInWithGoogle = false) }
                        stepChannel.send(step)
                    }
                    .onFailure { error -> fail(error.toGoogleSignInFailure()) }
            }
            .onFailure { error -> fail(error.toGoogleSignInFailure()) }
    }

    private fun chooseLocalOnly() {
        if (choiceJob?.isActive == true) return

        _uiState.update { it.copy(isWorking = true) }
        choiceJob = viewModelScope.launch {
            // The choice is a preference, not the lifter's data. If writing it fails the
            // worst case is being asked again on the next launch, which is a far better
            // outcome than refusing to let somebody into an app that works entirely
            // offline. Nothing is lost either way, so the flow continues regardless.
            runCatching { continueLocally() }

            _uiState.update { it.copy(isConfirmingLocalOnly = false, isWorking = false) }
            stepChannel.send(AuthFlowStep.Finish(AuthFlowResult.ContinuedLocally))
        }
    }

    /**
     * Only the banner is used. This screen has no inputs for a field error to sit under, and
     * the server has none to give for a token it either accepted or did not.
     */
    private fun fail(failure: AuthFailure) {
        _uiState.update {
            it.copy(
                isSigningInWithGoogle = false,
                formError = failure.message ?: AccountMessages.GOOGLE_SIGN_IN_FAILED,
            )
        }
    }
}
