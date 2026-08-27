package com.griffgym.application

import com.griffgym.application.account.PostSignInAction
import com.griffgym.application.account.ResolvePostSignInActionUseCase
import com.griffgym.application.account.UpgradeLocalUserToAccountUseCase
import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.BackupProgress
import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.CloudStateSummary
import com.griffgym.domain.repository.LocalTrainingDataRepository
import com.griffgym.domain.repository.UserModeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val SESSION = AuthSession("user-1", "lifter@example.com")

/**
 * What happens to a lifter's training data the moment they sign in.
 *
 * Four situations, and three of them can destroy months of work if the wrong one is chosen:
 * running onboarding over an existing block, uploading nothing and calling it a backup, or
 * replacing a phone's history with an account's without asking. This is the most consequential
 * branch in the app and it had no tests.
 */
class ResolvePostSignInActionUseCaseTest {

    @Test
    fun `nothing anywhere means a brand new lifter`() = runTest {
        val action = resolve(cloud = CloudStateSummary.EMPTY, hasLocalData = false)

        assertEquals(PostSignInAction.StartOnboarding, action)
    }

    @Test
    fun `history on the phone and an empty account is the migration case`() = runTest {
        val action = resolve(cloud = CloudStateSummary.EMPTY, hasLocalData = true)

        // Emphatically not StartOnboarding: that would ask a lifter halfway through a block for
        // maxes they entered months ago, and building cycle 1 on top of it would bury the one
        // they are actually training.
        assertEquals(PostSignInAction.BackUpLocalData, action)
    }

    @Test
    fun `an account with history and an empty phone is a new device`() = runTest {
        val action = resolve(cloud = CloudStateSummary.POPULATED, hasLocalData = false)

        assertEquals(PostSignInAction.RestoreCloudData, action)
    }

    @Test
    fun `data in both places is never resolved automatically`() = runTest {
        val action = resolve(cloud = CloudStateSummary.POPULATED, hasLocalData = true)

        // The two may well be the same history — this phone signing in again after the app was
        // reinstalled without the database being wiped — but nothing available here can tell
        // that from two unrelated histories with enough confidence to overwrite either.
        assertEquals(PostSignInAction.ResolveConflict, action)
    }

    @Test
    fun `a server that cannot be reached decides nothing at all`() = runTest {
        val repository = FakeCloudBackupRepository(
            summary = Result.failure(IllegalStateException("offline")),
        )

        val result = ResolvePostSignInActionUseCase(
            cloudBackupRepository = repository,
            localTrainingDataRepository = FakeLocalTrainingDataRepository(hasData = true),
        ).invoke(SESSION)

        // Failing is the safe outcome. Guessing "the cloud must be empty" would send a lifter
        // with a real backup down the upload path and overwrite it.
        assertTrue(result.isFailure)
    }

    private suspend fun resolve(
        cloud: CloudStateSummary,
        hasLocalData: Boolean,
    ): PostSignInAction = ResolvePostSignInActionUseCase(
        cloudBackupRepository = FakeCloudBackupRepository(summary = Result.success(cloud)),
        localTrainingDataRepository = FakeLocalTrainingDataRepository(hasLocalData),
    ).invoke(SESSION).getOrThrow()
}

/**
 * Turning a local-only lifter into an account holder.
 *
 * One rule, and everything here exists to hold it: the app is marked authenticated only after
 * the upload has actually succeeded. Getting that backwards would leave somebody believing they
 * had a backup that does not exist — which is worse than having no backup at all, because they
 * would stop worrying about it.
 */
class UpgradeLocalUserToAccountUseCaseTest {

    @Test
    fun `a successful upload is what marks the account live`() = runTest {
        val userMode = FakeUserModeRepository()
        val backup = FakeCloudBackupRepository()

        val result = UpgradeLocalUserToAccountUseCase(backup, userMode).invoke(SESSION)

        assertTrue(result.isSuccess)
        assertEquals(1, backup.backupCalls)
        assertEquals(UserMode.Authenticated("user-1", "lifter@example.com"), userMode.mode)
    }

    @Test
    fun `a failed upload leaves the app local-only`() = runTest {
        val userMode = FakeUserModeRepository()
        val backup = FakeCloudBackupRepository(
            backupResult = Result.failure(IllegalStateException("no signal")),
        )

        val result = UpgradeLocalUserToAccountUseCase(backup, userMode).invoke(SESSION)

        assertTrue(result.isFailure)
        // The single most important assertion in this file.
        assertEquals(UserMode.Undecided, userMode.mode)
    }

    @Test
    fun `progress is reported so the screen can say what it is doing`() = runTest {
        val reported = mutableListOf<BackupProgress>()

        UpgradeLocalUserToAccountUseCase(FakeCloudBackupRepository(), FakeUserModeRepository())
            .invoke(SESSION) { reported += it }

        assertTrue("no progress was reported at all", reported.isNotEmpty())
    }

    @Test
    fun `a second migration cannot start while one is running`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backup = FakeCloudBackupRepository(blockUntil = gate)
        val useCase = UpgradeLocalUserToAccountUseCase(backup, FakeUserModeRepository())

        val first = async { useCase.invoke(SESSION) }
        while (!useCase.isRunning) { kotlinx.coroutines.yield() }

        // The obvious way to start a second one is a lifter tapping the button twice.
        val second = useCase.invoke(SESSION)

        assertTrue(second.isFailure)

        gate.complete(Unit)
        assertTrue(first.await().isSuccess)
        assertEquals("the history was uploaded twice", 1, backup.backupCalls)
    }

    @Test
    fun `the lock is released so a retry after a failure can work`() = runTest {
        val backup = FakeCloudBackupRepository(
            backupResult = Result.failure(IllegalStateException("no signal")),
        )
        val userMode = FakeUserModeRepository()
        val useCase = UpgradeLocalUserToAccountUseCase(backup, userMode)

        assertTrue(useCase.invoke(SESSION).isFailure)
        assertFalse("the migration lock was never released", useCase.isRunning)

        backup.backupResult = Result.success(Unit)

        assertTrue(useCase.invoke(SESSION).isSuccess)
        assertEquals(UserMode.Authenticated("user-1", "lifter@example.com"), userMode.mode)
    }
}

// -------------------------------------------------------------------------------------------

private class FakeCloudBackupRepository(
    private val summary: Result<CloudStateSummary> = Result.success(CloudStateSummary.EMPTY),
    var backupResult: Result<Unit> = Result.success(Unit),
    private val blockUntil: CompletableDeferred<Unit>? = null,
) : CloudBackupRepository {

    var backupCalls: Int = 0
        private set

    override suspend fun readCloudSummary(): Result<CloudStateSummary> = summary

    override suspend fun backupLocalState(
        onProgress: suspend (BackupProgress) -> Unit,
    ): Result<Unit> {
        backupCalls++
        blockUntil?.await()
        return backupResult
    }

    override suspend fun restoreCloudState(): Result<Unit> = Result.success(Unit)
    override suspend fun pushPendingChanges(): Result<Int> = Result.success(0)
    override suspend fun countPendingChanges(): Int = 0
    override suspend fun clearLocalAccountData() = Unit
}

private class FakeLocalTrainingDataRepository(
    private val hasData: Boolean,
) : LocalTrainingDataRepository {
    override suspend fun hasAnyTrainingData(): Boolean = hasData
}

