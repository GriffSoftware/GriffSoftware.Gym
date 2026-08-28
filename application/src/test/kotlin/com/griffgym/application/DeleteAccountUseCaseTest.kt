package com.griffgym.application

import com.griffgym.application.account.DeleteAccountUseCase
import com.griffgym.application.account.GetStartupDestinationUseCase
import com.griffgym.application.account.GetUserModeUseCase
import com.griffgym.application.account.RestoreSessionUseCase
import com.griffgym.application.account.StartupDestination
import com.griffgym.application.onboarding.GetAppInitializationStateUseCase
import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.BackupProgress
import com.griffgym.domain.model.CloudSyncStatus
import com.griffgym.domain.model.CloudSyncState
import com.griffgym.domain.model.GriffGymError
import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.AuthRepository
import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.CloudStateSummary
import com.griffgym.domain.repository.CloudSyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order of operations in an irreversible action.
 *
 * Three properties are worth more than everything else this class does, and each has a test
 * whose failure would be a data-loss bug rather than a cosmetic one:
 *
 *  - **Nothing local is touched until the server has confirmed.** A wipe on a failed call
 *    destroys the lifter's training while leaving the account and its backup exactly where
 *    they were — and no screen afterwards can offer either back.
 *  - **The sync workers are cancelled before Room is emptied.** A background pass waking up
 *    mid-wipe reads a half-cleared database and starts reconciling it against an account the
 *    server no longer has.
 *  - **The stored identity is cleared before the database.** These writes are not one
 *    transaction, so a process death between them must not leave the app coming back as a
 *    signed-in lifter for an account that no longer exists.
 *
 * The fakes record the order they were called in, because "it called all four" is not the
 * property that matters.
 */
class DeleteAccountUseCaseTest {

    private val auth = FakeDeletableAuthRepository()
    private val sync = RecordingSyncStatusRepository()
    private val backup = RecordingCloudBackupRepository()
    private val userMode = RecordingUserModeRepository(UserMode.Authenticated(USER_ID, EMAIL))
    private val onboarding = RecordingOnboardingRepository(completed = true)

    private val calls = mutableListOf<String>()

    private val useCase: DeleteAccountUseCase
        get() = DeleteAccountUseCase(
            authRepository = auth.also { it.log = calls },
            cloudSyncStatusRepository = sync.also { it.log = calls },
            cloudBackupRepository = backup.also { it.log = calls },
            userModeRepository = userMode.also { it.log = calls },
            onboardingRepository = onboarding.also { it.log = calls },
        )

    @Test
    fun `a confirmed deletion clears every trace of the account from this phone`() = runTest {
        auth.result = Result.success(Unit)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, auth.deleteCalls)
        assertEquals(1, sync.cancelCalls)
        assertEquals(1, backup.clearCalls)
        assertEquals(UserMode.Undecided, userMode.mode)
        assertFalse(onboarding.isOnboardingCompleted())
    }

    /**
     * The server first, then the workers, then the identity, then the data.
     *
     * Asserted as a sequence rather than as four independent counters, because every one of
     * these orderings is load-bearing and four passing counters would notice none of them:
     * cancelling the workers after the wipe leaves a pass free to run through it, and clearing
     * the user mode after the wipe leaves the crash window this order exists to close.
     */
    @Test
    fun `it deletes on the server, then stops the workers, then forgets who the lifter was`() =
        runTest {
            auth.result = Result.success(Unit)

            useCase()

            assertEquals(
                listOf("api.delete", "sync.cancel", "mode.clear", "onboarding.clear", "room.clear"),
                calls,
            )
        }

    /**
     * The crash window, stated as the invariant that closes it.
     *
     * The four local steps span WorkManager, DataStore and Room, so they cannot be one
     * transaction and the app has to survive being killed between any two of them. The state
     * that must never be left behind is a stored mode of `Authenticated` over a database still
     * holding the deleted account's training: the credentials are already gone by the time
     * cleanup starts, so the next launch would resolve straight to `Ready` and open onto Home
     * showing that account's history, with nothing left to discover the truth from.
     *
     * Clearing the mode before the wipe is what rules that out, and every prefix of the
     * remaining work then resolves to the entry screen. Asserted as an ordering rather than by
     * simulating a kill, because the failure is the absence of later steps rather than an
     * exception — and a fake that threw would exercise `attempt`, which is a different case
     * covered separately below.
     */
    @Test
    fun `the stored identity is cleared before the database, so no crash can leave it stranded`() =
        runTest {
            auth.result = Result.success(Unit)

            useCase()

            assertTrue(
                "The user mode must be cleared before the wipe, or a process death between " +
                    "them leaves the app signed in to a deleted account. Order was: $calls",
                calls.indexOf("mode.clear") < calls.indexOf("room.clear"),
            )
        }

    @Test
    fun `a server error leaves the phone exactly as it was`() = runTest {
        auth.result = Result.failure(GriffGymError.Server(500))

        val result = useCase()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GriffGymError.Server)
        assertUntouched()
    }

    /**
     * Offline. Deliberately no queued retry and no "delete when the connection returns" — a
     * deletion nobody is watching is a deletion nobody consented to.
     */
    @Test
    fun `being offline deletes nothing, here or later`() = runTest {
        auth.result = Result.failure(GriffGymError.Network())

        val result = useCase()

        assertTrue(result.isFailure)
        assertUntouched()

        // And nothing was armed to happen on its own afterwards.
        assertEquals(0, sync.requestCalls)
    }

    /**
     * The refresh failed too. The account is untouched and the lifter must sign in again —
     * signing them out locally here, or wiping the database as a consolation, would destroy
     * training data belonging to an account that still exists.
     */
    @Test
    fun `a session that cannot be refreshed deletes nothing and does not sign anybody out`() =
        runTest {
            auth.result = Result.failure(GriffGymError.Unauthorized())

            val result = useCase()

            assertTrue(result.exceptionOrNull() is GriffGymError.Unauthorized)
            assertUntouched()
            assertEquals(UserMode.Authenticated(USER_ID, EMAIL), userMode.mode)
        }

    /**
     * By the time the cleanup runs the account is gone, and saying otherwise would be a lie.
     * A failure in one step must not stop the ones behind it: leaving the app holding a user
     * mode that names an account the server has already destroyed, with no way back into it,
     * is a worse outcome than whatever went wrong in the first place.
     *
     * The failure is injected into the *first* step on purpose. Breaking the last one would
     * prove nothing about whether the others still run.
     */
    @Test
    fun `an early cleanup step that fails does not stop the ones behind it`() = runTest {
        auth.result = Result.success(Unit)
        sync.failWith = IllegalStateException("WorkManager is not available")

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(UserMode.Undecided, userMode.mode)
        assertFalse(onboarding.isOnboardingCompleted())
        assertEquals(1, backup.clearCalls)
    }

    /** And the same when the wipe itself is the thing that breaks. */
    @Test
    fun `a failed wipe still leaves the app out of the deleted account`() = runTest {
        auth.result = Result.success(Unit)
        backup.failWith = IllegalStateException("Room transaction failed")

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(UserMode.Undecided, userMode.mode)
        assertFalse(onboarding.isOnboardingCompleted())
    }

    /**
     * The whole point of clearing the flag, checked against the code that actually reads it.
     *
     * Deletion has to leave the phone in the state a fresh install is in — not at a sign-in
     * form. Getting the flag right but the data wrong, or the other way round, produces the
     * two failures this asserts against: setup offered on top of a plan the lifter is halfway
     * through, or a Home screen with nothing behind it. So this runs the real deletion and
     * then asks the real startup resolver where the next launch goes.
     */
    @Test
    fun `after a deletion the next launch goes to the entry screen and then to first-run setup`() =
        runTest {
            // Empty on purpose: the deletion emptied Room, so there is nothing left for the
            // resolver to recognise this installation by. The default fixture models a phone
            // that already holds training, which is the opposite of the case under test.
            val programs = FakeTrainingProgramRepository(
                initialTemplates = emptyList(),
                programExists = false,
            )
            val referenceMaxes = FakeReferenceMaxRepository(initial = emptyList())
            auth.result = Result.success(Unit)

            useCase()

            val startup = GetStartupDestinationUseCase(
                getUserMode = GetUserModeUseCase(userMode),
                restoreSession = RestoreSessionUseCase(auth),
                getAppInitializationState = GetAppInitializationStateUseCase(
                    onboardingRepository = onboarding,
                    referenceMaxRepository = referenceMaxes,
                    trainingProgramRepository = programs,
                    workoutSessionRepository = FakeWorkoutSessionRepository(),
                ),
            )

            assertEquals(StartupDestination.ChooseDataMode, startup())

            // ...and once they have chosen, setup — not an empty Home.
            userMode.chooseLocalOnly()
            assertEquals(StartupDestination.Onboarding, startup())
        }

    private suspend fun assertUntouched() {
        assertEquals(0, sync.cancelCalls)
        assertEquals(0, backup.clearCalls)
        assertEquals(UserMode.Authenticated(USER_ID, EMAIL), userMode.mode)
        assertTrue(onboarding.isOnboardingCompleted())
        assertEquals(0, onboarding.clearCalls)
    }

    private companion object {
        const val USER_ID = "user-1"
        const val EMAIL = "lifter@griffgym.test"
    }
}

// -------------------------------------------------------------------------------------------

private class FakeDeletableAuthRepository : AuthRepository {

    var result: Result<Unit> = Result.success(Unit)
    var log: MutableList<String> = mutableListOf()

    var deleteCalls: Int = 0
        private set

    override suspend fun deleteAccount(): Result<Unit> {
        deleteCalls++
        log += "api.delete"
        return result
    }

    override fun observeSession(): Flow<AuthSession?> = emptyFlow()
    override suspend fun register(email: String, password: String) =
        Result.failure<AuthSession>(UnsupportedOperationException())

    override suspend fun login(email: String, password: String) =
        Result.failure<AuthSession>(UnsupportedOperationException())

    override suspend fun loginWithGoogle(idToken: String) =
        Result.failure<AuthSession>(UnsupportedOperationException())

    override suspend fun logout(): Result<Unit> =
        error("Deleting an account is not signing out.")

    override suspend fun restoreSession(): AuthSession? = null
    override fun observeSessionExpired(): Flow<Boolean> = flowOf(false)
    override suspend fun acknowledgeSessionExpired() = Unit
}

private class RecordingSyncStatusRepository : CloudSyncStatusRepository {

    var log: MutableList<String> = mutableListOf()

    /** Set to make cancelling blow up, so the steps behind it can be checked. */
    var failWith: Throwable? = null

    var cancelCalls: Int = 0
        private set

    var requestCalls: Int = 0
        private set

    override suspend fun cancelScheduledSync() {
        cancelCalls++
        log += "sync.cancel"
        failWith?.let { throw it }
    }

    override fun observeStatus(): Flow<CloudSyncStatus> =
        MutableStateFlow(CloudSyncStatus(CloudSyncState.SYNCED, null, 0))

    override suspend fun requestSync() {
        requestCalls++
    }

    override suspend fun syncNow(): Result<Unit> = Result.success(Unit)
}

private class RecordingCloudBackupRepository : CloudBackupRepository {

    var log: MutableList<String> = mutableListOf()

    /** Set to make the Room wipe blow up, so the rest of the cleanup can be checked. */
    var failWith: Throwable? = null

    var clearCalls: Int = 0
        private set

    override suspend fun clearLocalAccountData() {
        clearCalls++
        log += "room.clear"
        failWith?.let { throw it }
    }

    override suspend fun readCloudSummary(): Result<CloudStateSummary> =
        Result.success(CloudStateSummary.POPULATED)

    override suspend fun backupLocalState(
        onProgress: suspend (BackupProgress) -> Unit,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun restoreCloudState(): Result<Unit> = Result.success(Unit)
    override suspend fun pushPendingChanges(): Result<Int> = Result.success(0)
    override suspend fun countPendingChanges(): Int = 0
}

/** Records that the mode was cleared, and when relative to the other cleanup steps. */
private class RecordingUserModeRepository(
    var mode: UserMode,
) : com.griffgym.domain.repository.UserModeRepository {

    var log: MutableList<String> = mutableListOf()

    override fun observeUserMode(): Flow<UserMode> = MutableStateFlow(mode)
    override suspend fun getUserMode(): UserMode = mode
    override suspend fun chooseLocalOnly() { mode = UserMode.LocalOnly }
    override suspend fun markAuthenticated(session: AuthSession) {
        mode = UserMode.Authenticated(session.userId, session.email)
    }

    override suspend fun clearAccount() {
        mode = UserMode.Undecided
        log += "mode.clear"
    }
}

private class RecordingOnboardingRepository(
    private var completed: Boolean,
) : com.griffgym.domain.repository.OnboardingRepository {

    var log: MutableList<String> = mutableListOf()

    var clearCalls: Int = 0
        private set

    override suspend fun isOnboardingCompleted(): Boolean = completed
    override suspend fun markOnboardingCompleted() { completed = true }

    override suspend fun clearOnboardingCompleted() {
        completed = false
        clearCalls++
        log += "onboarding.clear"
    }
}
