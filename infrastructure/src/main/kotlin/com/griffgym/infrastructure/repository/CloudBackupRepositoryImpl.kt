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
     * somebody else's training history sitting in it.
     */
    override suspend fun clearLocalAccountData() = localStateWriter.clearLocalTrainingData()
}
