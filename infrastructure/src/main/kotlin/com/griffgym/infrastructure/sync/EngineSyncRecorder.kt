package com.griffgym.infrastructure.sync

import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.UserModeRepository
import com.griffgym.infrastructure.database.entity.SyncEntityType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records local changes for the sync engine, and only for a lifter who has an account.
 *
 * The guard is not an optimisation. A local-only lifter has explicitly chosen not to have a
 * server, and filling their database with rows about records "waiting to upload" would be
 * bookkeeping for something that is never going to happen — and would show up as a pending
 * backup in a UI that should say, plainly, that there is no backup at all.
 */
@Singleton
class EngineSyncRecorder @Inject internal constructor(
    private val syncEngine: SyncEngine,
    private val scheduler: WorkManagerSyncScheduler,
    private val userModeRepository: UserModeRepository,
) : SyncRecorder {

    override suspend fun markPending(type: SyncEntityType, syncId: String) {
        if (!isAuthenticated()) return

        syncEngine.markPending(type, syncId)
    }

    override suspend fun requestSync() {
        if (!isAuthenticated()) return

        scheduler.requestSync()
    }

    private suspend fun isAuthenticated(): Boolean =
        userModeRepository.getUserMode() is UserMode.Authenticated
}
