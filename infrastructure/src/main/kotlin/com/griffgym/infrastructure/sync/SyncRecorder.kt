package com.griffgym.infrastructure.sync

import com.griffgym.infrastructure.database.entity.SyncEntityType

/**
 * The one thing the training repositories need to know about synchronisation: that something
 * changed and now differs from the server.
 *
 * Deliberately this narrow. `RoomWorkoutSessionRepository` exists to save a lifter's sets, and
 * it should not be able to trigger an upload, read a sync status or cancel a worker — it should
 * only be able to say "this record moved". Handing it the whole [SyncEngine] would put all of
 * that within reach of the hottest write path in the app.
 *
 * Public only because the Room repositories that call it are: the contract is deliberately
 * this narrow so that being visible costs nothing.
 *
 * Every method is a local database write and nothing more. Recording a change must never make
 * a lifter mid-set wait for a network call — that is the entire point of being offline-first,
 * and it is why the upload happens later, elsewhere, on a worker.
 */
interface SyncRecorder {

    suspend fun markPending(type: SyncEntityType, syncId: String)

    /**
     * Hints that now would be a good moment to back up — after a workout is completed, a cycle
     * created, a max changed. Queues background work; never blocks, never throws.
     */
    suspend fun requestSync()
}

/** A recorder for a build with no cloud features wired in, and for tests that do not care. */
object NoOpSyncRecorder : SyncRecorder {
    override suspend fun markPending(type: SyncEntityType, syncId: String) = Unit
    override suspend fun requestSync() = Unit
}
