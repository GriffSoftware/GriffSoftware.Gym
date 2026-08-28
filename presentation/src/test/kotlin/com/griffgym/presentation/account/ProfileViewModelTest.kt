package com.griffgym.presentation.account

import com.griffgym.application.account.DeleteAccountUseCase
import com.griffgym.application.account.GetUserModeUseCase
import com.griffgym.application.account.LogoutUseCase
import com.griffgym.application.sync.GetCloudSyncStatusUseCase
import com.griffgym.application.sync.SyncNowUseCase
import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.BackupProgress
import com.griffgym.domain.model.CloudSyncState
import com.griffgym.domain.model.CloudSyncStatus
import com.griffgym.domain.model.GriffGymError
import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.AuthRepository
import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.CloudStateSummary
import com.griffgym.domain.repository.CloudSyncStatusRepository
import com.griffgym.domain.repository.OnboardingRepository
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The profile screen's state machine, and in particular the one action in Griff Gym that
 * cannot be taken back.
 *
 * Every deletion test asserts on the repositories underneath rather than only on the UI
 * state, because the guarantee being made is not "the screen said nothing was removed" — it
 * is that nothing *was* removed. A failure that showed the right dialog while quietly
 * wiping Room would pass a state-only test and lose a lifter's training.
 *
 * The use cases are the real ones. They are the thing that orders the calls, and faking them
 * would test a diagram rather than the code that runs on a phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {

    /**
     * Unconfined, as in the other ViewModel tests here: `uiState` is produced on
     * `viewModelScope`, and a queueing dispatcher would leave it at its initial value until
     * advanced, which is not how it behaves in front of a screen.
     */
    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var auth: FakeAuthRepository
    private lateinit var userMode: RecordingUserModeRepository
    private lateinit var cloud: FakeCloudBackupRepository
    private lateinit var sync: RecordingCloudSyncStatusRepository
    private lateinit var onboarding: RecordingOnboardingRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        auth = FakeAuthRepository()
        userMode = RecordingUserModeRepository()
        cloud = FakeCloudBackupRepository()
        sync = RecordingCloudSyncStatusRepository()
        onboarding = RecordingOnboardingRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------- deletion, accepted

    /**
     * The whole point of the feature: the server confirms, and only then does the phone
     * forget the account — tokens, cached training, scheduled work, user mode and the
     * first-run flag, in that order.
     */
    @Test
    fun `a deletion the server accepts removes the account and every local trace of it`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val navigation = collectNavigation(viewModel)

            viewModel.deleteAccountThroughBothDialogs()
            advanceUntilIdle()

            assertEquals(1, auth.deleteInvocations)
            assertEquals(listOf(ProfileNavigationEvent.AccountDeleted), navigation)

            assertNull("the stored session must be gone", auth.session)
            assertFalse("Room must be emptied", cloud.hasLocalAccountData)
            assertEquals("scheduled syncs must be cancelled", 1, sync.cancellations)
            assertEquals(UserMode.Undecided, userMode.mode.value)
            assertFalse("the next launch must offer setup again", onboarding.isCompleted)
        }

    /**
     * None of this ordering is incidental.
     *
     * The workers are cancelled before the wipe, because a background pass waking up mid-wipe
     * would reconcile a half-cleared Room against an account that no longer exists. The stored
     * identity is cleared before the wipe too, because these writes are not one transaction:
     * a process death between them must not leave the next launch reading `Authenticated` over
     * a database still holding the deleted account's training.
     */
    @Test
    fun `the workers stop and the identity is forgotten before the database is emptied`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            collectNavigation(viewModel)

            viewModel.deleteAccountThroughBothDialogs()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "deleteAccount",
                    "cancelScheduledSync",
                    "clearAccount",
                    "clearOnboardingCompleted",
                    "clearLocalAccountData",
                ),
                order,
            )
        }

    // ---------------------------------------------------------------- deletion, refused

    /**
     * A 500 is the server saying the account is still there. Anything cleared here would be
     * destroyed on a phone whose owner still has an account holding the only other copy.
     */
    @Test
    fun `a server error leaves the account, the session and the training where they were`() =
        runTest(dispatcher) {
            auth.deleteResult = Result.failure(GriffGymError.Server(statusCode = 500))
            val viewModel = viewModel()
            val navigation = collectNavigation(viewModel)

            viewModel.deleteAccountThroughBothDialogs()
            advanceUntilIdle()

            val deletion = viewModel.uiState.value.deletion
            assertEquals(DeleteAccountStage.FAILURE, deletion.stage)
            assertEquals(DeleteAccountFailureUi.RETRYABLE, deletion.failure)
            assertFalse(deletion.isDeleting)
            assertTrue("a failure must not navigate anywhere", navigation.isEmpty())

            assertNothingWasRemoved()
        }

    /**
     * Offline is the failure most likely to be met in a basement gym, and the one where a
     * "delete when you're back online" would be worst: nobody consents to an irreversible
     * action happening hours later, unattended.
     */
    @Test
    fun `an offline deletion neither wipes anything nor queues itself for later`() =
        runTest(dispatcher) {
            auth.deleteResult = Result.failure(GriffGymError.Network())
            val viewModel = viewModel()
            val navigation = collectNavigation(viewModel)

            viewModel.deleteAccountThroughBothDialogs()
            advanceUntilIdle()

            val deletion = viewModel.uiState.value.deletion
            assertEquals(DeleteAccountStage.FAILURE, deletion.stage)
            assertEquals(DeleteAccountFailureUi.RETRYABLE, deletion.failure)
            assertFalse(deletion.isDeleting)
            assertTrue(navigation.isEmpty())

            assertEquals("no retry may be scheduled", 0, sync.syncRequests)
            assertNothingWasRemoved()
        }

    /** No amount of retrying deletes an account the app can no longer prove it owns. */
    @Test
    fun `an expired session is reported as one rather than as something to retry`() =
        runTest(dispatcher) {
            auth.deleteResult = Result.failure(GriffGymError.Unauthorized())
            val viewModel = viewModel()
            val navigation = collectNavigation(viewModel)

            viewModel.deleteAccountThroughBothDialogs()
            advanceUntilIdle()

            val deletion = viewModel.uiState.value.deletion
            assertEquals(DeleteAccountStage.FAILURE, deletion.stage)
            assertEquals(DeleteAccountFailureUi.SESSION_EXPIRED, deletion.failure)
            assertTrue(navigation.isEmpty())

            assertNothingWasRemoved()
        }

    // ---------------------------------------------------------------- deletion, twice

    /**
     * The realistic double tap: a slow connection, a lifter who taps DELETE MY ACCOUNT
     * again because nothing appeared to happen.
     */
    @Test
    fun `a second tap while the deletion is in flight does not delete twice`() =
        runTest(dispatcher) {
            val held = CompletableDeferred<Result<Unit>>()
            auth.heldDeletion = held
            val viewModel = viewModel()
            val navigation = collectNavigation(viewModel)

            viewModel.deleteAccountThroughBothDialogs()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.deletion.isDeleting)
            assertFalse(viewModel.uiState.value.deletion.canConfirmDeletion)

            viewModel.onEvent(ProfileUiEvent.ConfirmDeleteAccount)
            viewModel.onEvent(ProfileUiEvent.ConfirmDeleteAccount)
            advanceUntilIdle()
            assertEquals(1, auth.deleteInvocations)

            held.complete(Result.success(Unit))
            advanceUntilIdle()

            assertEquals(1, auth.deleteInvocations)
            assertEquals(listOf(ProfileNavigationEvent.AccountDeleted), navigation)
        }

    /**
     * The other half of the race: a deletion fast enough to finish between the two taps, so
     * the in-flight job is gone by the time the second one arrives. What stops it then is
     * the latch, and the latch is also what keeps CANCEL from tearing the dialog down over
     * an account that has already been destroyed.
     */
    @Test
    fun `a second tap after a fast deletion does not delete twice`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val navigation = collectNavigation(viewModel)

        viewModel.deleteAccountThroughBothDialogs()
        advanceUntilIdle()
        assertEquals(listOf(ProfileNavigationEvent.AccountDeleted), navigation)

        viewModel.onEvent(ProfileUiEvent.ConfirmDeleteAccount)
        viewModel.onEvent(ProfileUiEvent.RetryDeleteAccount)
        viewModel.onEvent(ProfileUiEvent.DismissDeleteAccount)
        advanceUntilIdle()

        assertEquals(1, auth.deleteInvocations)
        assertEquals(listOf(ProfileNavigationEvent.AccountDeleted), navigation)
        // The dismiss is ignored too: the host is tearing this graph down, and a dialog that
        // closed itself first would show a Profile screen for an account that is gone.
        assertEquals(
            DeleteAccountStage.CONFIRMATION,
            viewModel.uiState.value.deletion.stage,
        )
        assertTrue(viewModel.uiState.value.deletion.isDeleting)
    }

    // ---------------------------------------------------------------- the confirmation gate

    /**
     * Trimmed, and case-sensitive on what is left. The soft keyboard adds the whitespace and
     * it carries no intent; the capitals are the deliberate act the second stage exists to
     * require, and the field asks for them.
     */
    @Test
    fun `the confirmation phrase is matched trimmed and case-sensitively`() {
        listOf("", " ", "delete", "Delete", "DELETEE", "DEL", "D E L E T E").forEach { typed ->
            assertFalse(
                "'$typed' must not arm the confirm button",
                DeleteAccountUiState(confirmationInput = typed).canConfirmDeletion,
            )
        }
        listOf("DELETE", " DELETE ", "\tDELETE\n").forEach { typed ->
            assertTrue(
                "'$typed' is the phrase and must arm the confirm button",
                DeleteAccountUiState(confirmationInput = typed).canConfirmDeletion,
            )
        }
    }

    /** Typed correctly, but already being acted on. */
    @Test
    fun `the phrase alone does not re-arm a confirmation that is already running`() {
        val deletion = DeleteAccountUiState(confirmationInput = "DELETE", isDeleting = true)

        assertTrue(deletion.isConfirmationPhraseTyped)
        assertFalse(deletion.canConfirmDeletion)
    }

    /**
     * The gate is not only a disabled button. An event that reached the ViewModel anyway —
     * a stale click, a test, a future caller — must not delete an account.
     */
    @Test
    fun `confirming without the phrase does not call the server at all`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val navigation = collectNavigation(viewModel)

        viewModel.onEvent(ProfileUiEvent.DeleteAccountRequested)
        viewModel.onEvent(ProfileUiEvent.DeleteAccountExplained)
        viewModel.onEvent(ProfileUiEvent.DeleteConfirmationChanged("delete"))
        viewModel.onEvent(ProfileUiEvent.ConfirmDeleteAccount)
        advanceUntilIdle()

        assertEquals(0, auth.deleteInvocations)
        assertEquals(DeleteAccountStage.CONFIRMATION, viewModel.uiState.value.deletion.stage)
        assertTrue(navigation.isEmpty())
        assertNothingWasRemoved()
    }

    // ---------------------------------------------------------------- stages

    @Test
    fun `the two questions are asked in order and neither deletes anything`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            collectNavigation(viewModel)

            assertEquals(DeleteAccountStage.NONE, viewModel.uiState.value.deletion.stage)

            viewModel.onEvent(ProfileUiEvent.DeleteAccountRequested)
            assertEquals(
                DeleteAccountStage.EXPLANATION,
                viewModel.uiState.value.deletion.stage,
            )

            viewModel.onEvent(ProfileUiEvent.DeleteAccountExplained)
            assertEquals(
                DeleteAccountStage.CONFIRMATION,
                viewModel.uiState.value.deletion.stage,
            )

            advanceUntilIdle()
            assertEquals(0, auth.deleteInvocations)
            assertNothingWasRemoved()
        }

    /**
     * The regression this guards: reopening the flow with the previous attempt's DELETE
     * still in the field would arm the confirm button before the lifter had typed a
     * character.
     */
    @Test
    fun `a phrase from an abandoned attempt does not pre-arm the second attempt`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            collectNavigation(viewModel)

            viewModel.onEvent(ProfileUiEvent.DeleteAccountRequested)
            viewModel.onEvent(ProfileUiEvent.DeleteAccountExplained)
            viewModel.onEvent(ProfileUiEvent.DeleteConfirmationChanged("DELETE"))
            assertTrue(viewModel.uiState.value.deletion.canConfirmDeletion)

            viewModel.onEvent(ProfileUiEvent.DismissDeleteAccount)
            assertEquals(DeleteAccountUiState(), viewModel.uiState.value.deletion)

            viewModel.onEvent(ProfileUiEvent.DeleteAccountRequested)
            viewModel.onEvent(ProfileUiEvent.DeleteAccountExplained)

            val deletion = viewModel.uiState.value.deletion
            assertEquals(DeleteAccountStage.CONFIRMATION, deletion.stage)
            assertEquals("", deletion.confirmationInput)
            assertFalse(deletion.canConfirmDeletion)
        }

    @Test
    fun `cancelling closes the dialogs and deletes nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val navigation = collectNavigation(viewModel)

        viewModel.onEvent(ProfileUiEvent.DeleteAccountRequested)
        viewModel.onEvent(ProfileUiEvent.DismissDeleteAccount)
        advanceUntilIdle()

        assertEquals(DeleteAccountUiState(), viewModel.uiState.value.deletion)
        assertEquals(0, auth.deleteInvocations)
        assertTrue(navigation.isEmpty())
        assertNothingWasRemoved()
    }

    /**
     * The call is already at the server and cannot be recalled. Closing the dialog would
     * only hide an operation that is still going to finish.
     */
    @Test
    fun `cancelling while the deletion is in flight is ignored`() = runTest(dispatcher) {
        val held = CompletableDeferred<Result<Unit>>()
        auth.heldDeletion = held
        val viewModel = viewModel()
        val navigation = collectNavigation(viewModel)

        viewModel.deleteAccountThroughBothDialogs()
        advanceUntilIdle()

        viewModel.onEvent(ProfileUiEvent.DismissDeleteAccount)

        val deletion = viewModel.uiState.value.deletion
        assertEquals(DeleteAccountStage.CONFIRMATION, deletion.stage)
        assertTrue(deletion.isDeleting)

        held.complete(Result.success(Unit))
        advanceUntilIdle()
        assertEquals(listOf(ProfileNavigationEvent.AccountDeleted), navigation)
    }

    // ---------------------------------------------------------------- retry

    /**
     * TRY AGAIN is an ordinary second attempt: the account still exists, so it goes back to
     * the confirmation dialog and calls the same endpoint again.
     */
    @Test
    fun `retrying after a failure returns to the confirmation and deletes on success`() =
        runTest(dispatcher) {
            auth.deleteResult = Result.failure(GriffGymError.Network())
            val viewModel = viewModel()
            val navigation = collectNavigation(viewModel)

            viewModel.deleteAccountThroughBothDialogs()
            advanceUntilIdle()
            assertEquals(DeleteAccountStage.FAILURE, viewModel.uiState.value.deletion.stage)

            auth.deleteResult = Result.success(Unit)
            viewModel.onEvent(ProfileUiEvent.RetryDeleteAccount)
            advanceUntilIdle()

            assertEquals(2, auth.deleteInvocations)
            assertEquals(
                DeleteAccountStage.CONFIRMATION,
                viewModel.uiState.value.deletion.stage,
            )
            assertNull(viewModel.uiState.value.deletion.failure)
            assertEquals(listOf(ProfileNavigationEvent.AccountDeleted), navigation)
            assertNull(auth.session)
            assertFalse(cloud.hasLocalAccountData)
        }

    /** TRY AGAIN can only come from a failure. Anywhere else it is not a second attempt. */
    @Test
    fun `retrying from anywhere but a failure does nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectNavigation(viewModel)

        viewModel.onEvent(ProfileUiEvent.DeleteAccountRequested)
        viewModel.onEvent(ProfileUiEvent.DeleteAccountExplained)
        viewModel.onEvent(ProfileUiEvent.RetryDeleteAccount)
        advanceUntilIdle()

        assertEquals(0, auth.deleteInvocations)
        assertNothingWasRemoved()
    }

    // ---------------------------------------------------------------- sign out

    /**
     * The reversible exit, unchanged by any of the above. Signing out clears this device;
     * the account and its backup stay exactly where they are.
     */
    @Test
    fun `signing out ends the session and leaves the screen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val navigation = collectNavigation(viewModel)

        viewModel.onEvent(ProfileUiEvent.SignOutRequested)
        assertTrue(viewModel.uiState.value.isConfirmingSignOut)

        viewModel.onEvent(ProfileUiEvent.ConfirmSignOut)
        advanceUntilIdle()

        assertEquals(1, auth.logoutInvocations)
        assertEquals(listOf(ProfileNavigationEvent.SignedOut), navigation)
        assertFalse(viewModel.uiState.value.isConfirmingSignOut)
        assertEquals(UserMode.Undecided, userMode.mode.value)
        // Signing out is not deleting an account: nothing server-side is touched, and the
        // first-run flag stays set because the lifter's setup still exists.
        assertEquals(0, auth.deleteInvocations)
        assertTrue(onboarding.isCompleted)
    }

    @Test
    fun `a double tap on sign out signs out once`() = runTest(dispatcher) {
        val held = CompletableDeferred<Result<Unit>>()
        auth.heldLogout = held
        val viewModel = viewModel()
        val navigation = collectNavigation(viewModel)

        viewModel.onEvent(ProfileUiEvent.ConfirmSignOut)
        viewModel.onEvent(ProfileUiEvent.ConfirmSignOut)
        advanceUntilIdle()

        held.complete(Result.success(Unit))
        advanceUntilIdle()

        assertEquals(1, auth.logoutInvocations)
        assertEquals(listOf(ProfileNavigationEvent.SignedOut), navigation)
    }

    // ---------------------------------------------------------------- state mapping

    @Test
    fun `the screen shows the signed-in address, the backup state and when it last landed`() =
        runTest(dispatcher) {
            sync.status.value = CloudSyncStatus(
                state = CloudSyncState.SYNCED,
                lastSyncedAt = Instant.parse("2026-08-28T16:42:00Z"),
            )
            val viewModel = viewModel()
            collectNavigation(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(SESSION.email, state.email)
            assertEquals(BackupStatusUi.BACKED_UP, state.status)
            assertEquals("Today, 16:42", state.lastBackupLabel)
            assertFalse(state.isSyncing)
        }

    /** Absent rather than "never": a first sync that has not landed yet is not a fault. */
    @Test
    fun `there is no last backup label before a sync has ever finished`() =
        runTest(dispatcher) {
            sync.status.value = CloudSyncStatus(state = CloudSyncState.PENDING)
            val viewModel = viewModel()
            collectNavigation(viewModel)
            advanceUntilIdle()

            assertEquals(BackupStatusUi.PENDING, viewModel.uiState.value.status)
            assertNull(viewModel.uiState.value.lastBackupLabel)
        }

    /** A running sync is reported as one, wherever the app learned about it. */
    @Test
    fun `a sync running in the background shows as syncing`() = runTest(dispatcher) {
        sync.status.value = CloudSyncStatus(state = CloudSyncState.SYNCING)
        val viewModel = viewModel()
        collectNavigation(viewModel)
        advanceUntilIdle()

        assertEquals(BackupStatusUi.BACKING_UP, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.isSyncing)
    }

    // ---------------------------------------------------------------- helpers

    /**
     * `uiState` and `navigation` only produce while collected, so a test has to stand in for
     * the screen. Collected for the whole test rather than awaited, so "nothing was emitted"
     * is testable.
     */
    private fun TestScope.collectNavigation(
        viewModel: ProfileViewModel,
    ): List<ProfileNavigationEvent> {
        val events = mutableListOf<ProfileNavigationEvent>()
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        backgroundScope.launch(dispatcher) { viewModel.navigation.collect(events::add) }
        return events
    }

    /** Everything a lifter has to do to actually delete an account, and nothing less. */
    private fun ProfileViewModel.deleteAccountThroughBothDialogs() {
        onEvent(ProfileUiEvent.DeleteAccountRequested)
        onEvent(ProfileUiEvent.DeleteAccountExplained)
        onEvent(ProfileUiEvent.DeleteConfirmationChanged("DELETE"))
        onEvent(ProfileUiEvent.ConfirmDeleteAccount)
    }

    /** The assertion the whole feature is judged on when the server says no. */
    private fun assertNothingWasRemoved() {
        assertEquals("the session must survive a failed deletion", SESSION, auth.session)
        assertTrue("Room must be untouched", cloud.hasLocalAccountData)
        assertEquals(UserMode.Authenticated(SESSION.userId, SESSION.email), userMode.mode.value)
        assertEquals(0, sync.cancellations)
        assertTrue("setup must not be forgotten", onboarding.isCompleted)
        assertTrue("no local cleanup step may have run: $order", order.isEmpty())
    }

    private fun viewModel(clock: Clock = FIXED_CLOCK): ProfileViewModel = ProfileViewModel(
        getUserMode = GetUserModeUseCase(userMode),
        getCloudSyncStatus = GetCloudSyncStatusUseCase(sync),
        syncNow = SyncNowUseCase(sync),
        logout = LogoutUseCase(auth, cloud, userMode),
        deleteAccount = DeleteAccountUseCase(
            authRepository = auth,
            cloudSyncStatusRepository = sync,
            cloudBackupRepository = cloud,
            userModeRepository = userMode,
            onboardingRepository = onboarding,
        ),
        clock = clock,
    )

    /** Shared by the fakes so the order of the local cleanup can be asserted. */
    private val order = mutableListOf<String>()

    private companion object {
        val SESSION = AuthSession(userId = "user-1", email = "lifter@griffgym.test")

        /** 19:00 UTC on the day the fixtures sync, so "Today, 16:42" is stable anywhere. */
        val FIXED_CLOCK: Clock =
            Clock.fixed(Instant.parse("2026-08-28T19:00:00Z"), ZoneOffset.UTC)
    }

    /** The credentials on this device. Cleared by the server confirming, and by nothing else. */
    private inner class FakeAuthRepository : AuthRepository {

        var session: AuthSession? = SESSION

        var deleteResult: Result<Unit> = Result.success(Unit)

        /** Set to keep the call "at the server" until the test decides how it ends. */
        var heldDeletion: CompletableDeferred<Result<Unit>>? = null
        var heldLogout: CompletableDeferred<Result<Unit>>? = null

        var deleteInvocations: Int = 0
            private set

        var logoutInvocations: Int = 0
            private set

        override suspend fun deleteAccount(): Result<Unit> {
            deleteInvocations++
            val result = heldDeletion?.await() ?: deleteResult
            return result.onSuccess {
                order += "deleteAccount"
                session = null
            }
        }

        override suspend fun logout(): Result<Unit> {
            logoutInvocations++
            heldLogout?.await()
            session = null
            return Result.success(Unit)
        }

        override fun observeSession(): Flow<AuthSession?> = emptyFlow()

        override suspend fun register(email: String, password: String): Result<AuthSession> =
            error("The profile screen never registers.")

        override suspend fun login(email: String, password: String): Result<AuthSession> =
            error("The profile screen never signs in.")

        override suspend fun loginWithGoogle(idToken: String): Result<AuthSession> =
            error("The profile screen never signs in.")

        override suspend fun restoreSession(): AuthSession? = session

        override fun observeSessionExpired(): Flow<Boolean> = emptyFlow()

        override suspend fun acknowledgeSessionExpired() = Unit
    }

    private inner class RecordingUserModeRepository : UserModeRepository {

        val mode = MutableStateFlow<UserMode>(
            UserMode.Authenticated(SESSION.userId, SESSION.email),
        )

        override fun observeUserMode(): Flow<UserMode> = mode

        override suspend fun getUserMode(): UserMode = mode.value

        override suspend fun chooseLocalOnly() {
            mode.value = UserMode.LocalOnly
        }

        override suspend fun markAuthenticated(session: AuthSession) {
            mode.value = UserMode.Authenticated(session.userId, session.email)
        }

        override suspend fun clearAccount() {
            order += "clearAccount"
            mode.value = UserMode.Undecided
        }
    }

    /** Stands in for Room: [hasLocalAccountData] is this device's training history. */
    private inner class FakeCloudBackupRepository : CloudBackupRepository {

        var hasLocalAccountData: Boolean = true
            private set

        override suspend fun clearLocalAccountData() {
            order += "clearLocalAccountData"
            hasLocalAccountData = false
        }

        override suspend fun readCloudSummary(): Result<CloudStateSummary> =
            Result.success(CloudStateSummary.POPULATED)

        override suspend fun backupLocalState(
            onProgress: suspend (BackupProgress) -> Unit,
        ): Result<Unit> = error("The profile screen never uploads a first backup.")

        override suspend fun restoreCloudState(): Result<Unit> =
            error("The profile screen never restores.")

        override suspend fun pushPendingChanges(): Result<Int> = Result.success(0)

        override suspend fun countPendingChanges(): Int = 0
    }

    private inner class RecordingCloudSyncStatusRepository : CloudSyncStatusRepository {

        val status = MutableStateFlow(CloudSyncStatus(CloudSyncState.SYNCED))

        var syncRequests: Int = 0
            private set

        var cancellations: Int = 0
            private set

        override fun observeStatus(): Flow<CloudSyncStatus> = status

        override suspend fun requestSync() {
            syncRequests++
        }

        override suspend fun syncNow(): Result<Unit> = Result.success(Unit)

        override suspend fun cancelScheduledSync() {
            order += "cancelScheduledSync"
            cancellations++
        }
    }

    private inner class RecordingOnboardingRepository : OnboardingRepository {

        var isCompleted: Boolean = true
            private set

        override suspend fun isOnboardingCompleted(): Boolean = isCompleted

        override suspend fun markOnboardingCompleted() {
            isCompleted = true
        }

        override suspend fun clearOnboardingCompleted() {
            order += "clearOnboardingCompleted"
            isCompleted = false
        }
    }
}
