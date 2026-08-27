package com.griffgym.infrastructure.sync

import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.UserModeRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What synchronisation does when the app opens.
 *
 * Two things, both cheap and neither on the critical path: make sure the periodic safety net
 * is scheduled, and ask for a pass if anything is waiting.
 *
 * It deliberately does not wait for either. Home is drawn from Room, so a lifter opening the
 * app in a basement gym sees their training instantly and the backup catches up whenever it
 * can. Blocking startup on a network call would trade the app's best property — that it always
 * opens — for a status line nobody is looking at yet.
 */
@Singleton
class SyncBootstrap @Inject internal constructor(
    private val userModeRepository: UserModeRepository,
    private val scheduler: WorkManagerSyncScheduler,
) {

    suspend fun onApplicationStart() {
        if (userModeRepository.getUserMode() !is UserMode.Authenticated) {
            // A local-only lifter has no server. Leaving stale work scheduled would have the
            // system waking the app up periodically to discover, every time, that there is
            // nothing to do.
            scheduler.cancelAll()
            return
        }

        scheduler.schedulePeriodicSync()
        scheduler.requestSync()
    }
}
