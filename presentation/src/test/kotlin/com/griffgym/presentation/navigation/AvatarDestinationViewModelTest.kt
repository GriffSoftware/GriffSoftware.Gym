package com.griffgym.presentation.navigation

import com.griffgym.application.account.GetUserModeUseCase
import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.UserModeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Where the one avatar in the top bar leads.
 *
 * There is a single icon for two screens, so the only thing that can go wrong here is it
 * leading to the wrong one — a signed-in lifter dropped on the sign-up screen, or a lifter
 * without an account sent to a profile that does not exist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AvatarDestinationViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val userMode = FakeUserModeRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a signed-in lifter is taken to their profile`() = runTest(dispatcher) {
        userMode.mode.value = AUTHENTICATED
        val viewModel = viewModel()
        collectDestination(viewModel)
        advanceUntilIdle()

        assertEquals(Routes.PROFILE, viewModel.destination.value)
    }

    @Test
    fun `a local-only lifter is taken to the screen that offers an account`() =
        runTest(dispatcher) {
            userMode.mode.value = UserMode.LocalOnly
            val viewModel = viewModel()
            collectDestination(viewModel)
            advanceUntilIdle()

            assertEquals(Routes.ACCOUNT, viewModel.destination.value)
        }

    @Test
    fun `an undecided installation is taken to the account screen`() = runTest(dispatcher) {
        userMode.mode.value = UserMode.Undecided
        val viewModel = viewModel()
        collectDestination(viewModel)
        advanceUntilIdle()

        assertEquals(Routes.ACCOUNT, viewModel.destination.value)
    }

    /**
     * The icon is tappable before the flow has answered, and a tap that did nothing would
     * read as a broken button. Account is the safe default: correct for an undecided
     * installation, and a working screen for anyone who beats the first emission.
     *
     * The stored mode is deliberately withheld here — a flow that has not emitted is exactly
     * the state a cold DataStore read is in for the first frame or two.
     */
    @Test
    fun `the destination before anything has been read is the account screen`() =
        runTest(dispatcher) {
            val pending = PendingUserModeRepository()
            val viewModel = AvatarDestinationViewModel(GetUserModeUseCase(pending))
            collectDestination(viewModel)
            advanceUntilIdle()

            assertEquals(Routes.ACCOUNT, viewModel.destination.value)

            pending.mode.emit(AUTHENTICATED)
            advanceUntilIdle()
            assertEquals(Routes.PROFILE, viewModel.destination.value)
        }

    /**
     * The app shell is mounted for the whole session, including across the moment a lifter
     * signs in from the account screen — which is exactly when the right answer changes
     * underneath it.
     */
    @Test
    fun `signing in moves the avatar to the profile without remounting anything`() =
        runTest(dispatcher) {
            userMode.mode.value = UserMode.Undecided
            val viewModel = viewModel()
            collectDestination(viewModel)
            advanceUntilIdle()
            assertEquals(Routes.ACCOUNT, viewModel.destination.value)

            userMode.mode.value = AUTHENTICATED
            advanceUntilIdle()

            assertEquals(Routes.PROFILE, viewModel.destination.value)
        }

    private fun viewModel(): AvatarDestinationViewModel =
        AvatarDestinationViewModel(GetUserModeUseCase(userMode))

    /** `destination` only follows the flow while collected, so a test stands in for the bar. */
    private fun TestScope.collectDestination(viewModel: AvatarDestinationViewModel) {
        backgroundScope.launch(dispatcher) { viewModel.destination.collect { } }
    }

    private companion object {
        val AUTHENTICATED = UserMode.Authenticated("user-1", "lifter@griffgym.test")
    }

    /** A stored mode that has not been read back yet. Observation only. */
    private class PendingUserModeRepository : UserModeRepository {

        val mode = MutableSharedFlow<UserMode>()

        override fun observeUserMode(): Flow<UserMode> = mode

        override suspend fun getUserMode(): UserMode = error("The avatar only observes.")

        override suspend fun chooseLocalOnly() = error("The avatar changes nothing.")

        override suspend fun markAuthenticated(session: AuthSession) =
            error("The avatar changes nothing.")

        override suspend fun clearAccount() = error("The avatar changes nothing.")
    }

    private class FakeUserModeRepository : UserModeRepository {

        val mode = MutableStateFlow<UserMode>(UserMode.Undecided)

        override fun observeUserMode(): Flow<UserMode> = mode

        override suspend fun getUserMode(): UserMode = mode.value

        override suspend fun chooseLocalOnly() {
            mode.value = UserMode.LocalOnly
        }

        override suspend fun markAuthenticated(session: AuthSession) {
            mode.value = UserMode.Authenticated(session.userId, session.email)
        }

        override suspend fun clearAccount() {
            mode.value = UserMode.Undecided
        }
    }
}
