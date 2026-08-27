package com.griffgym.infrastructure.sync

import com.griffgym.domain.model.BackupProgress
import com.griffgym.domain.model.GriffGymError
import com.griffgym.infrastructure.sync.model.CloudSnapshot
import com.griffgym.infrastructure.sync.model.SnapshotCycle
import com.griffgym.infrastructure.sync.model.SnapshotExercise
import com.griffgym.infrastructure.sync.model.SnapshotReferenceMax
import com.griffgym.infrastructure.sync.model.SnapshotWorkout

/**
 * A server that does exactly what the test tells it to.
 *
 * Written by hand rather than mocked because what these tests need is a server that can *fail
 * in specific ways* — a version conflict on one record, a dropped connection on the next — and
 * then be asked what it actually received. That is a small state machine, and a small state
 * machine is clearer written out than assembled from stubbing calls.
 */
internal class FakeCloudStateGateway(
    var cloudState: CloudSnapshot = CloudSnapshot.Empty,
) : CloudStateGateway {

    /** Keyed by sync id. Set one to make just that record fail. */
    val failures = mutableMapOf<String, GriffGymError>()

    var uploadSnapshotFailure: GriffGymError? = null
    var fetchFailure: GriffGymError? = null

    val uploadedWorkoutIds = mutableListOf<String>()
    val uploadedCycleIds = mutableListOf<String>()
    val uploadedReferenceMaxIds = mutableListOf<String>()
    var uploadExerciseCalls: Int = 0
    var uploadSnapshotCalls: Int = 0
    val progressReported = mutableListOf<BackupProgress>()

    override suspend fun isCloudEmpty(): Boolean = cloudState.isEmpty

    override suspend fun fetchState(): CloudSnapshot {
        fetchFailure?.let { throw it }
        return cloudState
    }

    override suspend fun uploadSnapshot(
        snapshot: CloudSnapshot,
        onProgress: suspend (BackupProgress) -> Unit,
    ) {
        uploadSnapshotCalls += 1
        uploadSnapshotFailure?.let { throw it }

        cloudState = snapshot
        progressReported.forEach { }
    }

    override suspend fun uploadExercises(exercises: List<SnapshotExercise>) {
        uploadExerciseCalls += 1
    }

    override suspend fun uploadReferenceMax(
        referenceMax: SnapshotReferenceMax,
        expectedVersion: Int?,
    ): Int {
        failures[referenceMax.syncId]?.let { throw it }
        uploadedReferenceMaxIds += referenceMax.syncId
        return (expectedVersion ?: 0) + 1
    }

    override suspend fun uploadCycle(
        cycle: SnapshotCycle,
        exercises: List<SnapshotExercise>,
        expectedVersion: Int?,
    ): Int {
        failures[cycle.syncId]?.let { throw it }
        uploadedCycleIds += cycle.syncId
        return (expectedVersion ?: 0) + 1
    }

    override suspend fun uploadWorkout(workout: SnapshotWorkout, expectedVersion: Int?): Int {
        failures[workout.syncId]?.let { throw it }
        uploadedWorkoutIds += workout.syncId
        return (expectedVersion ?: 0) + 1
    }
}
