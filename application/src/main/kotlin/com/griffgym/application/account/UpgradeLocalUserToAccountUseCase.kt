package com.griffgym.application.account

import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.BackupProgress
import com.griffgym.domain.model.BackupStage
import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.UserModeRepository
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a local-only lifter into an account holder without touching their training data.
 *
 * The order is the whole point:
 *
 * ```
 * upload everything Room holds
 *        ↓  succeeded?
 * mark the app AUTHENTICATED
 * ```
 *
 * Never the other way round. If the upload fails, the app stays local-only, the lifter is
 * told their data is still safe on the phone, and they can try again. Marking the account
 * live first would leave somebody believing they had a backup that does not exist — the one
 * outcome this whole feature is meant to prevent.
 *
 * Nothing is reset, no empty cycle 1 is created, and onboarding is not run again. After a
 * successful backup the lifter has exactly the app they had a minute ago, plus a copy in
 * the cloud.
 */
@Singleton
class UpgradeLocalUserToAccountUseCase @Inject constructor(
    private val cloudBackupRepository: CloudBackupRepository,
    private val userModeRepository: UserModeRepository,
) {

    /**
     * One migration at a time, process-wide.
     *
     * Two concurrent uploads of the same history would race each other through idempotent
     * writes to no benefit and considerable confusion, and the obvious way to start a second
     * one is a lifter tapping a button twice.
     */
    private val migrationLock = Mutex()

    val isRunning: Boolean get() = migrationLock.isLocked

    suspend operator fun invoke(
        session: AuthSession,
        onProgress: suspend (BackupProgress) -> Unit = {},
    ): Result<Unit> {
        if (!migrationLock.tryLock()) {
            return Result.failure(IllegalStateException("A backup is already running."))
        }

        return try {
            onProgress(BackupProgress(BackupStage.PREPARING))

            cloudBackupRepository.backupLocalState(onProgress)
                .onSuccess {
                    onProgress(BackupProgress(BackupStage.DONE))
                    userModeRepository.markAuthenticated(session)
                }
        } finally {
            migrationLock.unlock()
        }
    }
}
