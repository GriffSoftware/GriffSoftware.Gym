package com.griffgym.presentation.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.griffgym.application.account.ContinueLocallyUseCase
import com.griffgym.application.account.GoogleLoginUseCase
import com.griffgym.application.account.InitializeAuthenticatedSessionUseCase
import com.griffgym.application.account.ResolvePostSignInActionUseCase
import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.BackupProgress
import com.griffgym.domain.model.CloudSyncStatus
import com.griffgym.domain.model.GriffGymError
import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.AuthRepository
import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.CloudStateSummary
import com.griffgym.domain.repository.CloudSyncStatusRepository
import com.griffgym.domain.repository.LocalTrainingDataRepository
import com.griffgym.domain.repository.UserModeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The entry screen's state machine, now that it can finish the whole of a sign-in itself.
 *
 * The three outcomes that matter are all here: a session that has to be routed rather than
 * simply reported, a dismissed account picker that must not look like a failure, and a
 * failure that must not look like anything else.
 *
 * Credential Manager itself is faked. It cannot run in a JVM test, and what is worth testing
 * is not Google's picker but what this ViewModel does with the three answers it can give.
 * Robolectric is here only to supply the `Context` the call takes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataProtectionViewModelTest {

    /**
     * Unconfined, for the same reason as `OnboardingViewModelTest`: `uiState` is updated on
     * `viewModelScope`, and a queueing dispatcher would leave it at its initial value until
     * advanced, which is not how it behaves in front of a screen.
     */
    private val dispatcher = UnconfinedTestDispatcher()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var launcher: FakeGoogleSignInLauncher
    private lateinit var auth: FakeAuthRepository
    private lateinit var userMode: RecordingUserModeRepository
    private lateinit var cloud: FakeCloudBackupRepository
    private lateinit var localData: FakeLocalTrainingDataRepository
    private lateinit var sync: RecordingCloudSyncStatusRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        launcher = FakeGoogleSignInLauncher()
        auth = FakeAuthRepository()
        userMode = RecordingUserModeRepository()
        cloud = FakeCloudBackupRepository()
        localData = FakeLocalTrainingDataRepository()
        sync = RecordingCloudSyncStatusRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * A brand new lifter: nothing on the phone, nothing in the account. The flow ends here,
     * marked authenticated, and the host is told to run first-run setup.
     */
    @Test
    fun `a google sign-in with nothing to move finishes the flow`() = runTest(dispatcher) {
        cloud.summary = CloudStateSummary.EMPTY
        localData.hasData = false
        val viewModel = viewModel()
        val steps = collectSteps(viewModel)

        viewModel.signInWithGoogle(context)
        advanceUntilIdle()

        assertEquals(ID_TOKEN, auth.googleIdTokens.single())
        assertEquals(listOf(AuthFlowStep.Finish(AuthFlowResult.NeedsOnboarding(SESSION))), steps)
        assertEquals(SESSION, userMode.authenticatedAs)
        assertEquals(1, sync.syncRequests)
        assertFalse(viewModel.uiState.value.isSigningInWithGoogle)
        assertNull(viewModel.uiState.value.formError)
    }

    /**
     * The reason this goes through [PostSignInRouter] rather than reporting a session and
     * being done: six months of local training and an empty account is a *backup*, and
     * nothing may be marked as backed up before a byte has been uploaded.
     */
    @Test
    fun `local training data and an empty account go to the backup screen`() = runTest(dispatcher) {
        cloud.summary = CloudStateSummary.EMPTY
        localData.hasData = true
        val viewModel = viewModel()
        val steps = collectSteps(viewModel)

        viewModel.signInWithGoogle(context)
        advanceUntilIdle()

        assertEquals(listOf(AuthFlowStep.BackUpLocalData(SESSION)), steps)
        assertNull("nothing may be marked authenticated before the data moves", userMode.authenticatedAs)
        assertEquals(0, sync.syncRequests)
    }

    /**
     * Dismissing the account picker is a decision, not a failure. An error banner for "I
     * changed my mind" is how an app teaches people to ignore its error banners.
     */
    @Test
    fun `a dismissed account picker says nothing and goes nowhere`() = runTest(dispatcher) {
        launcher.result = Result.failure(GoogleSignInException.Cancelled())
        val viewModel = viewModel()
        val steps = collectSteps(viewModel)

        viewModel.signInWithGoogle(context)
        advanceUntilIdle()

        assertTrue(steps.isEmpty())
        assertNull(viewModel.uiState.value.formError)
        assertFalse(viewModel.uiState.value.isSigningInWithGoogle)
        assertTrue("the token exchange must not be attempted", auth.googleIdTokens.isEmpty())
    }

    /** A phone with no Google account, or a build with no client id, says so. */
    @Test
    fun `an unavailable picker explains itself`() = runTest(dispatcher) {
        launcher.result = Result.failure(GoogleSignInException.Unavailable("no accounts"))
        val viewModel = viewModel()
        val steps = collectSteps(viewModel)

        viewModel.signInWithGoogle(context)
        advanceUntilIdle()

        assertTrue(steps.isEmpty())
        assertEquals(AccountMessages.GOOGLE_UNAVAILABLE, viewModel.uiState.value.formError)
        assertFalse(viewModel.uiState.value.isSigningInWithGoogle)
    }

    /**
     * What the server answers for an expired token — and for a deployment whose Google client
     * id is not configured yet. Never "that email and password do not match", which would be
     * nonsense on a screen where nobody typed either.
     */
    @Test
    fun `a rejected token surfaces a message rather than a session`() = runTest(dispatcher) {
        auth.googleResult = Result.failure(GriffGymError.Unauthorized("rejected"))
        val viewModel = viewModel()
        val steps = collectSteps(viewModel)

        viewModel.signInWithGoogle(context)
        advanceUntilIdle()

        assertTrue(steps.isEmpty())
        assertEquals(AccountMessages.GOOGLE_REJECTED, viewModel.uiState.value.formError)
        assertNull(userMode.authenticatedAs)
    }

    /** Offline is its own message, because it is the one failure that is nobody's fault. */
    @Test
    fun `no connection is reported as no connection`() = runTest(dispatcher) {
        auth.googleResult = Result.failure(GriffGymError.Network())
        val viewModel = viewModel()

        viewModel.signInWithGoogle(context)
        advanceUntilIdle()

        assertEquals(AccountMessages.NO_CONNECTION, viewModel.uiState.value.formError)
    }

    /** The picker is slow enough to invite a second tap, which must not open a second one. */
    @Test
    fun `a second tap while the picker is open is ignored`() = runTest(dispatcher) {
        val held = CompletableDeferred<Result<String>>()
        launcher.held = held
        val viewModel = viewModel()

        viewModel.signInWithGoogle(context)
        viewModel.signInWithGoogle(context)
        advanceUntilIdle()

        assertEquals(1, launcher.invocations)
        assertTrue(viewModel.uiState.value.isSigningInWithGoogle)

        held.complete(Result.success(ID_TOKEN))
        advanceUntilIdle()
        assertEquals(1, auth.googleIdTokens.size)
    }

    /**
     * The local-only path still ends the flow, now that it reports the same steps the Google
     * path does. Signing in and staying local are two answers to one question, and the screen
     * has one way of saying it is finished.
     */
    @Test
    fun `continuing locally records the choice and finishes`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val steps = collectSteps(viewModel)

        viewModel.onEvent(DataProtectionUiEvent.ConfirmContinueLocally)
        advanceUntilIdle()

        assertTrue(userMode.choseLocalOnly)
        assertEquals(listOf(AuthFlowStep.Finish(AuthFlowResult.ContinuedLocally)), steps)
        assertFalse(viewModel.uiState.value.isWorking)
    }

    private fun viewModel(): DataProtectionViewModel = DataProtectionViewModel(
        continueLocally = ContinueLocallyUseCase(userMode),
        googleLogin = GoogleLoginUseCase(auth),
        googleSignInLauncher = launcher,
        postSignInRouter = PostSignInRouter(
            resolvePostSignInAction = ResolvePostSignInActionUseCase(cloud, localData),
            initializeAuthenticatedSession = InitializeAuthenticatedSessionUseCase(userMode, sync),
        ),
    )

    /** Collected for the whole test rather than awaited, so "nothing was emitted" is testable. */
    private fun TestScope.collectSteps(viewModel: DataProtectionViewModel): List<AuthFlowStep> {
        val steps = mutableListOf<AuthFlowStep>()
        backgroundScope.launch(dispatcher) { viewModel.steps.collect(steps::add) }
        return steps
    }

    private companion object {
        const val ID_TOKEN = "eyJhbGciOiJSUzI1NiJ9.payload.signature"
        val SESSION = AuthSession(userId = "user-1", email = "lifter@griffgym.test")
    }

    private class FakeGoogleSignInLauncher : GoogleSignInLauncher {

        var result: Result<String> = Result.success(ID_TOKEN)

        /** Set to keep the picker "open" until the test decides how it ends. */
        var held: CompletableDeferred<Result<String>>? = null

        var invocations: Int = 0
            private set

        override suspend fun requestIdToken(context: Context): Result<String> {
            invocations++
            return held?.await() ?: result
        }
    }

    private class FakeAuthRepository : AuthRepository {

        var googleResult: Result<AuthSession> = Result.success(SESSION)

        val googleIdTokens = mutableListOf<String>()

        override suspend fun loginWithGoogle(idToken: String): Result<AuthSession> {
            googleIdTokens += idToken
            return googleResult
        }

        override fun observeSession(): Flow<AuthSession?> = emptyFlow()

        override suspend fun register(email: String, password: String): Result<AuthSession> =
            error("The entry screen never registers with a password.")

        override suspend fun login(email: String, password: String): Result<AuthSession> =
            error("The entry screen never signs in with a password.")

        override suspend fun logout(): Result<Unit> = error("Not part of this flow.")

        override suspend fun deleteAccount(): Result<Unit> = error("Not part of this flow.")

        override suspend fun restoreSession(): AuthSession? = null

        override fun observeSessionExpired(): Flow<Boolean> = emptyFlow()

        override suspend fun acknowledgeSessionExpired() = Unit
    }

    private class RecordingUserModeRepository : UserModeRepository {

        var choseLocalOnly: Boolean = false
            private set

        var authenticatedAs: AuthSession? = null
            private set

        override fun observeUserMode(): Flow<UserMode> = MutableStateFlow(UserMode.Undecided)

        override suspend fun getUserMode(): UserMode = UserMode.Undecided

        override suspend fun chooseLocalOnly() {
            choseLocalOnly = true
        }

        override suspend fun markAuthenticated(session: AuthSession) {
            authenticatedAs = session
        }

        override suspend fun clearAccount() = Unit
    }

    private class FakeCloudBackupRepository : CloudBackupRepository {

        var summary: CloudStateSummary = CloudStateSummary.EMPTY

        override suspend fun readCloudSummary(): Result<CloudStateSummary> = Result.success(summary)

        override suspend fun backupLocalState(
            onProgress: suspend (BackupProgress) -> Unit,
        ): Result<Unit> = error("The entry screen never uploads; the backup screen does.")

        override suspend fun restoreCloudState(): Result<Unit> = error("Not part of this flow.")

        override suspend fun pushPendingChanges(): Result<Int> = Result.success(0)

        override suspend fun countPendingChanges(): Int = 0

        override suspend fun clearLocalAccountData() = Unit
    }

    private class FakeLocalTrainingDataRepository : LocalTrainingDataRepository {
        var hasData: Boolean = false
        override suspend fun hasAnyTrainingData(): Boolean = hasData
    }

    private class RecordingCloudSyncStatusRepository : CloudSyncStatusRepository {

        var syncRequests: Int = 0
            private set

        override fun observeStatus(): Flow<CloudSyncStatus> = emptyFlow()

        override suspend fun requestSync() {
            syncRequests++
        }

        override suspend fun syncNow(): Result<Unit> = Result.success(Unit)

        override suspend fun cancelScheduledSync() = Unit
    }
}
