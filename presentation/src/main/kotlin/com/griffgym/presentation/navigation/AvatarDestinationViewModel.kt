package com.griffgym.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.account.GetUserModeUseCase
import com.griffgym.domain.model.UserMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Where the avatar in the top bar leads.
 *
 * There is one icon, not two, and it goes to whichever screen this installation actually has
 * an answer for: a lifter with an account gets their profile, a lifter without one gets the
 * screen that offers to make them one. Shipping two icons, or one that opens a screen with
 * half its rows greyed out, would be putting the app's internal distinction in front of
 * somebody who only wanted to tap their own face.
 *
 * The destination is read from persisted state rather than passed down from the host, because
 * the host is the app shell and it is mounted for the whole session — including across the
 * moment a lifter signs in from the account screen and the right answer changes underneath it.
 */
@HiltViewModel
class AvatarDestinationViewModel @Inject constructor(
    getUserMode: GetUserModeUseCase,
) : ViewModel() {

    /**
     * Starts at [Routes.ACCOUNT] and not at null.
     *
     * The flow resolves within a frame or two, but the icon is tappable immediately, and a
     * tap that did nothing would read as a broken button. Account is the safe default of the
     * two: it is correct for an undecided installation, and a signed-in lifter who beats the
     * first emission lands on a working screen rather than on nothing.
     */
    val destination: StateFlow<String> = getUserMode()
        .map { mode -> if (mode is UserMode.Authenticated) Routes.PROFILE else Routes.ACCOUNT }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = Routes.ACCOUNT,
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
