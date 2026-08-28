package com.griffgym.infrastructure.repository

import com.griffgym.domain.model.BackupProgress
import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.CloudStateSummary
import com.griffgym.infrastructure.sync.LocalStateWriter
import com.griffgym.infrastructure.sync.SyncEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The application layer's view of moving data between this phone and the account.
 *
 * A thin seam on purpose: the reasoning lives in [SyncEngine], and this exists so the use
 * cases depend on a domain contract rather than on an infrastructure class.
 */
@Singleton
internal class CloudBackupRepositoryImpl @Inject constructor(
    private val syncEngine: SyncEngine,
    private val localStateWriter: LocalStateWriter,
) : CloudBackupRepository {

    override suspend fun readCloudSummary(): Result<CloudStateSummary> =
        syncEngine.isCloudEmpty().map { empty ->
            if (empty) CloudStateSummary.EMPTY else CloudStateSummary.POPULATED
        }

    override suspend fun backupLocalState(
        onProgress: suspend (BackupProgress) -> Unit,
    ): Result<Unit> = syncEngine.backupEverything(onProgress)

    override suspend fun restoreCloudState(): Result<Unit> = syncEngine.restoreEverything()

    override suspend fun pushPendingChanges(): Result<Int> = syncEngine.pushPendingChanges()

    override suspend fun countPendingChanges(): Int = syncEngine.countPending()

    /**
     * Signing out clears this account's cached training data from the phone.
     *
     * The cloud copy is untouched — this is not deleting an account, and signing back in
     * restores everything. What it prevents is the next person to pick up the phone finding
     * somebody else's training history sitting in it. Account deletion uses the same wipe,
     * where the cloud copy has already gone.
     *
     * Held off against syncing rather than merely run after the workers are cancelled.
     * WorkManager cancellation is asynchronous and cooperative, so "cancelled" does not mean
     * "stopped": a pass already in flight carries on until it notices, and its writes would
     * otherwise be free to land after the wipe committed — leaving exactly the remnant this is
     * supposed to remove. The wipe itself is one Room transaction; the lock is what keeps that
     * transaction from racing a pass that is still finishing.
     */
    override suspend fun clearLocalAccountData() = syncEngine.withSyncHeldOff {
        localStateWriter.clearLocalTrainingData()
    }
}
