package com.griffgym.application.sync

import com.griffgym.domain.model.CloudSyncStatus
import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.CloudSyncStatusRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCloudSyncStatusUseCase @Inject constructor(
    private val cloudSyncStatusRepository: CloudSyncStatusRepository,
) {
    operator fun invoke(): Flow<CloudSyncStatus> = cloudSyncStatusRepository.observeStatus()
}

/**
 * The SYNC NOW button.
 *
 * A convenience, not the mechanism: synchronisation is automatic, and a lifter who never
 * touches this should still always be backed up.
 */
class SyncNowUseCase @Inject constructor(
    private val cloudSyncStatusRepository: CloudSyncStatusRepository,
) {
    suspend operator fun invoke(): Result<Unit> = cloudSyncStatusRepository.syncNow()
}

/**
 * Rebuilds this device's database from the account's backup.
 *
 * Atomic: one Room transaction. A restore that failed halfway would leave cycles without
 * their programs and sessions without their sets, and the app would have no way to tell
 * that from real data.
 */
class RestoreCloudStateUseCase @Inject constructor(
    private val cloudBackupRepository: CloudBackupRepository,
) {
    suspend operator fun invoke(): Result<Unit> = cloudBackupRepository.restoreCloudState()
}

/** Uploads everything Room holds. Used by the migration flow and by a manual re-backup. */
class BackupLocalStateUseCase @Inject constructor(
    private val cloudBackupRepository: CloudBackupRepository,
) {
    suspend operator fun invoke(
        onProgress: suspend (com.griffgym.domain.model.BackupProgress) -> Unit = {},
    ): Result<Unit> = cloudBackupRepository.backupLocalState(onProgress)
}
