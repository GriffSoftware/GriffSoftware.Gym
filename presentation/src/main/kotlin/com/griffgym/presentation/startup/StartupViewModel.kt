package com.griffgym.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.onboarding.AppInitializationState
import com.griffgym.application.onboarding.GetAppInitializationStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the very first frame of the app should be. */
sealed interface StartupUiState {

    /** Two existence queries wide — on screen for a frame or two, not a real splash. */
    data object Loading : StartupUiState

    data object Onboarding : StartupUiState

    data object Ready : StartupUiState
}

/**
 * Resolves, once per process, whether the lifter needs first-run setup.
 *
 * If the check itself fails the app opens normally rather than offering setup: showing
 * setup to someone who may already have a block would invite them to overwrite it, whereas
 * opening Home on an empty database is merely an empty Home.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val getAppInitializationState: GetAppInitializationStateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StartupUiState>(StartupUiState.Loading)
    val uiState: StateFlow<StartupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = runCatching { getAppInitializationState() }
                .fold(
                    onSuccess = { state ->
                        when (state) {
                            AppInitializationState.NeedsOnboarding -> StartupUiState.Onboarding
                            AppInitializationState.Ready -> StartupUiState.Ready
                        }
                    },
                    onFailure = { StartupUiState.Ready },
                )
        }
    }

    /** Called once the program has been generated and setup marked complete. */
    fun onOnboardingCompleted() {
        _uiState.value = StartupUiState.Ready
    }
}
