package com.griffgym.infrastructure.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.griffgym.domain.model.CloudSyncState
import com.griffgym.domain.model.CloudSyncStatus
import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.CloudSyncStatusRepository
import com.griffgym.domain.repository.NetworkMonitor
import com.griffgym.domain.repository.UserModeRepository
import com.griffgym.infrastructure.database.dao.SyncMetadataDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when a backup happens, and answers "is my training safe?" for the UI.
 *
 * Synchronisation is automatic. The SYNC NOW button exists, but a lifter who never touches it
 * should still always be backed up — so the interesting work here is the scheduling, not the
 * button.
 */
@Singleton
internal class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userModeRepository: UserModeRepository,
    private val syncMetadataDao: SyncMetadataDao,
    private val networkMonitor: NetworkMonitor,
    private val syncEngine: SyncEngine,
) : CloudSyncStatusRepository {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun observeStatus(): Flow<CloudSyncStatus> = combine(
        combine(
            userModeRepository.observeUserMode(),
            syncMetadataDao.observeOutstandingCount(),
            syncMetadataDao.observeConflictCount(),
            syncMetadataDao.observeLastSyncedAtUtc(),
            networkMonitor.observeIsOnline(),
            ::StoredStatus,
        ),
        // A genuine input rather than a property read at recombination time, so a pass
        // starting or finishing is itself a reason for the status to change.
        syncEngine.isSyncing,
    ) { stored, isSyncing ->
        val (mode, outstanding, conflicts, lastSyncedMillis, isOnline) = stored
        // The timestamp comes from writes the server actually accepted, so "last backup" can
        // never creep forward because a sync merely started.
        val lastSyncedAt = lastSyncedMillis?.let(Instant::ofEpochMilli)

        val state = when {
            mode !is UserMode.Authenticated -> CloudSyncState.LOCAL_ONLY
            conflicts > 0 -> CloudSyncState.CONFLICT
            isSyncing -> CloudSyncState.SYNCING
            outstanding > 0 && !isOnline -> CloudSyncState.OFFLINE
            outstanding > 0 -> CloudSyncState.PENDING
            !isOnline -> CloudSyncState.OFFLINE
            else -> CloudSyncState.SYNCED
        }

        CloudSyncStatus(state = state, lastSyncedAt = lastSyncedAt, pendingChanges = outstanding)
    }.distinctUntilChanged()

    override suspend fun requestSync() {
        if (userModeRepository.getUserMode() !is UserMode.Authenticated) return

        workManager.enqueueUniqueWork(
            GriffGymSyncWorker.UNIQUE_ONE_OFF_WORK,
            // KEEP, not REPLACE: several things ask for a sync within moments of each other —
            // the app opening, a workout completing, a max changing — and cancelling a pass
            // that is already uploading to start an identical one achieves nothing.
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<GriffGymSyncWorker>()
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    /** Runs a pass inline and waits for it. Only the SYNC NOW button needs this. */
    override suspend fun syncNow(): Result<Unit> =
        syncEngine.pushPendingChanges().map { }

    /**
     * The contract account deletion is written against. Same work as [cancelAll]; named for
     * the caller that has to guarantee nothing will touch Room behind its back.
     */
    override suspend fun cancelScheduledSync() = cancelAll()

    /**
     * A safety net rather than the main mechanism: changes are pushed as they happen, and this
     * catches whatever was left behind by a process that died mid-pass or a phone that spent
     * three days in a drawer.
     */
    fun schedulePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            GriffGymSyncWorker.UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<GriffGymSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build(),
        )
    }

    /**
     * Cancels both unique works. Already-running passes are cancelled too, which is safe:
     * [SyncEngine] uploads by `syncId`, so a pass cut short has either written a record or
     * not, and the next one repeats it rather than duplicating it.
     */
    fun cancelAll() {
        workManager.cancelUniqueWork(GriffGymSyncWorker.UNIQUE_PERIODIC_WORK)
        workManager.cancelUniqueWork(GriffGymSyncWorker.UNIQUE_ONE_OFF_WORK)
    }

    /**
     * The five stored signals, grouped so they can be combined with the in-flight flag without
     * exceeding what `combine` accepts as separate arguments.
     */
    private data class StoredStatus(
        val mode: UserMode,
        val outstanding: Int,
        val conflicts: Int,
        val lastSyncedMillis: Long?,
        val isOnline: Boolean,
    )

    /**
     * Connectivity is a precondition for scheduling, not a promise the request will succeed —
     * a captive portal reports a healthy network. Every request still handles its own failure.
     */
    private val networkConstraints: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}
