package com.griffgym.infrastructure.sync

import com.griffgym.domain.model.BackupProgress
import com.griffgym.infrastructure.sync.model.CloudSnapshot
import com.griffgym.infrastructure.sync.model.SnapshotCycle
import com.griffgym.infrastructure.sync.model.SnapshotExercise
import com.griffgym.infrastructure.sync.model.SnapshotReferenceMax
import com.griffgym.infrastructure.sync.model.SnapshotWorkout

/**
 * Everything synchronisation needs from the server, expressed in snapshots rather than in
 * HTTP.
 *
 * The sync engine's job is deciding *what* to send and *when*, and reacting sensibly when the
 * answer comes back wrong. None of that reasoning should be entangled with Retrofit interfaces,
 * request DTOs or status codes — so it is not: the engine talks to this, and one adapter
 * underneath it knows about the wire.
 *
 * Failures are thrown as `GriffGymError`, never as `HttpException` or `IOException`. The engine
 * decides what to do based on the *kind* of failure, and it can only do that if the kinds have
 * names.
 */
internal interface CloudStateGateway {

    /** True when the account holds no training data at all — a freshly registered lifter. */
    suspend fun isCloudEmpty(): Boolean

    /** The account's whole state, as a snapshot ready to be written to Room. */
    suspend fun fetchState(): CloudSnapshot

    /**
     * Uploads a lifter's entire local history.
     *
     * Idempotent by sync id at every level, so a retry after a timeout re-sends the same
     * records rather than creating a second copy of the same six months of training.
     */
    suspend fun uploadSnapshot(
        snapshot: CloudSnapshot,
        onProgress: suspend (BackupProgress) -> Unit,
    )

    /** Ensures the movement catalogue exists server-side before anything references it. */
    suspend fun uploadExercises(exercises: List<SnapshotExercise>)

    /** Returns the record's new server version, for the next write's conflict check. */
    suspend fun uploadReferenceMax(referenceMax: SnapshotReferenceMax, expectedVersion: Int?): Int

    suspend fun uploadCycle(
        cycle: SnapshotCycle,
        exercises: List<SnapshotExercise>,
        expectedVersion: Int?,
    ): Int

    suspend fun uploadWorkout(workout: SnapshotWorkout, expectedVersion: Int?): Int
}
