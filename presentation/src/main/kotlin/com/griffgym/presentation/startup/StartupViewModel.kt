package com.griffgym.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.account.GetStartupDestinationUseCase
import com.griffgym.application.account.StartupDestination
import com.griffgym.presentation.account.AuthFlowResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the very first frame of the app should be. */
sealed interface StartupUiState {

    /** A handful of local reads wide — on screen for a frame or two, not a real splash. */
    data object Loading : StartupUiState

    /**
     * The lifter has not said where their data should live. Shown once, and shown to an
     * installation holding two years of training just as much as to a fresh one.
     */
    data object ChoosingDataMode : StartupUiState

    data object Onboarding : StartupUiState

    data object Ready : StartupUiState
}

/**
 * Resolves, once per process, where the app opens — and never on the network.
 *
 * A signed-in lifter in a basement gym opens to their own training exactly as fast as one on
 * wifi, because everything this reads is local. Whether the backup is reachable is a question
 * answered later, quietly, by the sync worker.
 *
 * If the check itself fails the app opens normally rather than offering setup: showing setup
 * to someone who may already have a block would invite them to overwrite it, whereas opening
 * Home on an empty database is merely an empty Home.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val getStartupDestination: GetStartupDestinationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StartupUiState>(StartupUiState.Loading)
    val uiState: StateFlow<StartupUiState> = _uiState.asStateFlow()

    init {
        resolve()
    }

    /**
     * Called when the entry flow reports back.
     *
     * Every one of these outcomes has already been written down — the mode persisted, the
     * backup uploaded, the restore committed — so this only decides what to draw next.
     */
    fun onAuthFlowComplete(result: AuthFlowResult) {
        when (result) {
            // Local-only is now recorded, but whether this lifter needs first-run setup is a
            // separate question: they may have been training on this phone for months.
            AuthFlowResult.ContinuedLocally -> resolve()

            is AuthFlowResult.NeedsOnboarding -> _uiState.value = StartupUiState.Onboarding

            is AuthFlowResult.Authenticated,
            is AuthFlowResult.Restored,
            -> _uiState.value = StartupUiState.Ready
        }
    }

    /** Called once the program has been generated and setup marked complete. */
    fun onOnboardingCompleted() {
        _uiState.value = StartupUiState.Ready
    }

    /**
     * Signing out tears the whole app graph down and returns to the entry screen.
     *
     * Not merely cosmetic: the account's cached training data has just been cleared from this
     * device, so leaving the app mounted would show a lifter's screens backed by an empty
     * database — and, worse, leave the next person holding the phone somewhere inside them.
     */
    fun onSignedOut() {
        _uiState.value = StartupUiState.ChoosingDataMode
    }

    private fun resolve() {
        _uiState.value = StartupUiState.Loading

        viewModelScope.launch {
            _uiState.value = runCatching { getStartupDestination() }
                .fold(
                    onSuccess = { destination ->
                        when (destination) {
                            StartupDestination.ChooseDataMode -> StartupUiState.ChoosingDataMode
                            StartupDestination.Onboarding -> StartupUiState.Onboarding
                            StartupDestination.Ready -> StartupUiState.Ready
                        }
                    },
                    onFailure = { StartupUiState.Ready },
                )
        }
    }
}
