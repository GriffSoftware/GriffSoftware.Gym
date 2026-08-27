package com.griffgym.infrastructure.sync

import com.griffgym.domain.model.BackupProgress
import com.griffgym.domain.model.GriffGymError
import com.griffgym.infrastructure.database.dao.SyncMetadataDao
import com.griffgym.infrastructure.database.entity.SyncEntityType
import com.griffgym.infrastructure.database.entity.SyncMetadataEntity
import com.griffgym.infrastructure.database.entity.SyncState
import com.griffgym.infrastructure.sync.model.CloudSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that moves training data between Room and the server.
 *
 * A component of its own, and deliberately not folded into an auth repository or a ViewModel.
 * Synchronisation has its own lifecycle — it runs from a background worker, from the app
 * opening, and from a button — and it needs to keep working when nothing is on screen at all.
 *
 * Two rules shape all of it:
 *
 *  - **Room is written first and the network is never in the way.** A set logged in a basement
 *    gym is saved and visible immediately; whether it has reached a server is a separate fact,
 *    tracked separately.
 *  - **A failure never costs data.** A conflict marks the record and keeps the local copy. A
 *    network error leaves everything pending. Nothing here deletes anything on the way to
 *    resolving a disagreement.
 */
@Singleton
internal class SyncEngine @Inject constructor(
    private val gateway: CloudStateGateway,
    private val localStateReader: LocalStateReader,
    private val localStateWriter: LocalStateWriter,
    private val syncMetadataDao: SyncMetadataDao,
    private val clock: Clock,
) {

    /**
     * One sync at a time, process-wide.
     *
     * The worker, the app opening and the SYNC NOW button can all fire within a second of each
     * other. Two passes running at once would upload the same records twice and race each
     * other's metadata writes, so the second caller waits rather than duplicating the work.
     */
    private val syncLock = Mutex()

    private val _isSyncing = MutableStateFlow(false)

    /**
     * A flow, not a `Boolean` read on demand.
     *
     * The status the account screen shows is assembled with `combine`, which only recomputes
     * when one of its inputs *emits*. A plain property would be sampled whenever some unrelated
     * flow happened to fire, so a backup could finish and the screen carry on saying "syncing"
     * until something else moved — or a fast pass never show at all.
     */
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Runs [block] under the sync lock while reporting that a pass is in flight. */
    private suspend fun <T> syncing(block: suspend () -> T): T = syncLock.withLock {
        _isSyncing.value = true
        try {
            block()
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Sends whatever is waiting. Returns how many records the server accepted.
     *
     * Records that fail are marked and left alone — a failed upload is a record still safely on
     * the phone, and the next pass will try it again.
     */
    suspend fun pushPendingChanges(): Result<Int> = syncing {
        runCatching {
            val outstanding = syncMetadataDao.getOutstanding()
                .filter { it.syncState != SyncState.CONFLICT }

            if (outstanding.isEmpty()) return@runCatching 0

            val snapshot = localStateReader.read()

            // The catalogue goes first: a template or a log that references a movement the
            // server has never heard of would be rejected, and the rejection would look like a
            // conflict rather than an ordering mistake.
            gateway.uploadExercises(snapshot.exercises)

            var accepted = 0

            outstanding.forEach { metadata ->
                if (uploadOne(metadata, snapshot)) accepted += 1
            }

            accepted
        }
    }

    /**
     * Uploads a lifter's entire history — the local-to-account migration.
     *
     * Marks everything SYNCED only after the upload returns successfully. Recording a backup
     * that did not happen is the single worst thing this class could do: the lifter would stop
     * worrying about a copy that does not exist.
     */
    suspend fun backupEverything(
        onProgress: suspend (BackupProgress) -> Unit,
    ): Result<Unit> = syncing {
        runCatching {
            val snapshot = localStateReader.read()

            gateway.uploadSnapshot(snapshot, onProgress)

            markSynced(snapshot)
        }
    }

    /**
     * Replaces the local database with the account's copy.
     *
     * The write itself is one Room transaction, so a failure anywhere leaves the phone exactly
     * as it was rather than half restored.
     */
    suspend fun restoreEverything(): Result<Unit> = syncing {
        runCatching {
            val snapshot = gateway.fetchState()

            localStateWriter.replaceLocalState(snapshot, clock.instant())
        }
    }

    suspend fun isCloudEmpty(): Result<Boolean> = runCatching { gateway.isCloudEmpty() }

    suspend fun countPending(): Int = syncMetadataDao.countOutstanding()

    /**
     * Records that something changed locally and now differs from the server.
     *
     * Called on the write path — completing a workout, creating a cycle, changing a max — and
     * nothing else. It writes one small row; it does not touch the network, so a lifter mid-set
     * never waits for it.
     */
    suspend fun markPending(type: SyncEntityType, syncId: String) {
        val existing = syncMetadataDao.get(type, syncId)

        // A record already in conflict stays in conflict. Quietly downgrading it to "pending"
        // would send the local version again and lose whatever the server had.
        if (existing?.syncState == SyncState.CONFLICT) return

        syncMetadataDao.upsert(
            SyncMetadataEntity(
                entityType = type,
                entityId = syncId,
                syncState = SyncState.PENDING_UPLOAD,
                serverVersion = existing?.serverVersion,
                lastAttemptAtUtc = existing?.lastAttemptAtUtc,
                lastSyncedAtUtc = existing?.lastSyncedAtUtc,
                failureMessage = null,
            ),
        )
    }

    // -----------------------------------------------------------------------------------------

    /** True when the server accepted it. */
    private suspend fun uploadOne(
        metadata: SyncMetadataEntity,
        snapshot: CloudSnapshot,
    ): Boolean {
        val now = clock.instant().toEpochMilli()

        return try {
            val version = when (metadata.entityType) {
                SyncEntityType.EXERCISE -> {
                    // Already sent above, as a batch, before anything could reference one.
                    null
                }

                SyncEntityType.REFERENCE_MAX -> snapshot.referenceMaxes
                    .firstOrNull { it.syncId == metadata.entityId }
                    ?.let { gateway.uploadReferenceMax(it, metadata.serverVersion) }

                SyncEntityType.TRAINING_CYCLE -> snapshot.cycles
                    .firstOrNull { it.syncId == metadata.entityId }
                    ?.let { gateway.uploadCycle(it, snapshot.exercises, metadata.serverVersion) }

                SyncEntityType.WORKOUT_SESSION -> snapshot.workouts
                    .firstOrNull { it.syncId == metadata.entityId }
                    ?.let { gateway.uploadWorkout(it, metadata.serverVersion) }
            }

            syncMetadataDao.upsert(
                metadata.copy(
                    syncState = SyncState.SYNCED,
                    serverVersion = version ?: metadata.serverVersion,
                    lastAttemptAtUtc = now,
                    lastSyncedAtUtc = now,
                    failureMessage = null,
                ),
            )

            true
        } catch (error: GriffGymError) {
            syncMetadataDao.upsert(
                metadata.copy(
                    // A version conflict is not a failure to retry: the server has a newer
                    // revision, and sending this one again would overwrite it. The record is
                    // parked, with the local copy intact, until something can resolve it.
                    syncState = when (error) {
                        is GriffGymError.VersionConflict, is GriffGymError.Conflict ->
                            SyncState.CONFLICT
                        else -> SyncState.FAILED
                    },
                    serverVersion = (error as? GriffGymError.VersionConflict)?.actualVersion
                        ?: metadata.serverVersion,
                    lastAttemptAtUtc = now,
                    failureMessage = error.message,
                ),
            )

            // Some failures are about this record; others are about the whole pass. A dropped
            // connection or a dead session will defeat every remaining upload too, so the pass
            // stops and reports — rather than marking a hundred records failed for one reason
            // and telling the worker everything went fine.
            if (error is GriffGymError.Network || error is GriffGymError.Unauthorized) throw error

            false
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (unexpected: Exception) {
            // A record the mappers cannot make sense of — a reference max filed under a
            // category that has no lift, say. Everything below is supposed to surface as a
            // GriffGymError, so this is a bug rather than a condition; but one malformed row
            // must not stop every healthy record behind it from being backed up.
            syncMetadataDao.upsert(
                metadata.copy(
                    syncState = SyncState.FAILED,
                    lastAttemptAtUtc = now,
                    failureMessage = unexpected.message ?: unexpected::class.simpleName,
                ),
            )

            false
        }
    }

    private suspend fun markSynced(snapshot: CloudSnapshot) {
        val now = clock.instant().toEpochMilli()

        fun entry(type: SyncEntityType, id: String) = SyncMetadataEntity(
            entityType = type,
            entityId = id,
            syncState = SyncState.SYNCED,
            serverVersion = null,
            lastAttemptAtUtc = now,
            lastSyncedAtUtc = now,
            failureMessage = null,
        )

        syncMetadataDao.upsertAll(
            buildList {
                snapshot.exercises.forEach { add(entry(SyncEntityType.EXERCISE, it.syncId)) }
                snapshot.referenceMaxes.forEach {
                    add(entry(SyncEntityType.REFERENCE_MAX, it.syncId))
                }
                snapshot.cycles.forEach { add(entry(SyncEntityType.TRAINING_CYCLE, it.syncId)) }
                snapshot.workouts.forEach { add(entry(SyncEntityType.WORKOUT_SESSION, it.syncId)) }
            },
        )
    }
}
