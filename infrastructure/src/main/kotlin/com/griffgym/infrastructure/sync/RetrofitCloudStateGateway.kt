package com.griffgym.infrastructure.sync

import com.griffgym.domain.model.BackupProgress
import com.griffgym.domain.model.BackupStage
import com.griffgym.domain.model.GriffGymError
import com.griffgym.infrastructure.network.ApiErrorMapper
import com.griffgym.infrastructure.network.GriffGymApi
import com.griffgym.infrastructure.network.dto.ApplicationStateResponseDto
import com.griffgym.infrastructure.network.dto.UpdateReferenceMaxRequestDto
import com.griffgym.infrastructure.sync.mapper.requiredLift
import com.griffgym.infrastructure.sync.mapper.toCreateRequest
import com.griffgym.infrastructure.sync.mapper.toLiftDtoOrNull
import com.griffgym.infrastructure.sync.mapper.toRequest
import com.griffgym.infrastructure.sync.mapper.toSnapshot
import com.griffgym.infrastructure.sync.model.CloudSnapshot
import com.griffgym.infrastructure.sync.model.SnapshotCycle
import com.griffgym.infrastructure.sync.model.SnapshotExercise
import com.griffgym.infrastructure.sync.model.SnapshotReferenceMax
import com.griffgym.infrastructure.sync.model.SnapshotWorkout
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only class that knows both HTTP and snapshots.
 *
 * Every method here converts what the wire gives back into a `GriffGymError` before it leaves,
 * so the sync engine above can reason about *what kind* of failure it hit — a conflict to park,
 * a dropped connection to retry — rather than pattern-matching on a networking library's
 * exception types.
 */
@Singleton
internal class RetrofitCloudStateGateway @Inject constructor(
    private val api: GriffGymApi,
    private val errorMapper: ApiErrorMapper,
) : CloudStateGateway {

    override suspend fun isCloudEmpty(): Boolean = call {
        val state = api.applicationState()

        // "Empty" means no *training* data. A freshly registered account has a profile and a
        // sync version already, and treating those as content would send a new phone down the
        // conflict path on its very first sign-in.
        state.cycles.isEmpty() && state.workouts.isEmpty() && state.referenceMaxes.isEmpty()
    }

    override suspend fun fetchState(): CloudSnapshot = call {
        val state = api.applicationState()

        requireSupportedSchema(state)

        state.toSnapshot()
    }

    override suspend fun uploadSnapshot(
        snapshot: CloudSnapshot,
        onProgress: suspend (BackupProgress) -> Unit,
    ) {
        onProgress(BackupProgress(BackupStage.PREPARING))

        // Order matters and is not cosmetic. Movements first, because templates and logs
        // reference them; cycles before workouts, because a session names the cycle it belongs
        // to. Sending them the other way round produces rejections that look like conflicts.
        uploadExercises(snapshot.exercises)

        val maxes = snapshot.referenceMaxes.filter { it.category.toLiftDtoOrNull() != null }
        maxes.forEachIndexed { index, max ->
            onProgress(
                BackupProgress(BackupStage.UPLOADING_REFERENCE_MAXES, index, maxes.size),
            )
            uploadReferenceMax(max, expectedVersion = null)
        }

        snapshot.cycles.sortedBy { it.cycleNumber }.forEachIndexed { index, cycle ->
            onProgress(
                BackupProgress(BackupStage.UPLOADING_CYCLES, index, snapshot.cycles.size),
            )
            uploadCycle(cycle, snapshot.exercises, expectedVersion = null)
        }

        snapshot.workouts.sortedBy { it.startedAt }.forEachIndexed { index, workout ->
            onProgress(
                BackupProgress(BackupStage.UPLOADING_WORKOUTS, index, snapshot.workouts.size),
            )
            uploadWorkout(workout, expectedVersion = null)
        }

        onProgress(BackupProgress(BackupStage.VERIFYING))

        // Read it back. A backup nobody checked is a backup nobody can rely on, and this is the
        // one moment where the whole point of the feature is on the line.
        verifyUploaded(snapshot)
    }

    override suspend fun uploadExercises(exercises: List<SnapshotExercise>) {
        if (exercises.isEmpty()) return

        // There is no bulk exercise endpoint; the catalogue rides along with a cycle. When
        // there are no cycles yet there is also nothing that could reference a movement, so
        // there is genuinely nothing to do here.
    }

    override suspend fun uploadReferenceMax(
        referenceMax: SnapshotReferenceMax,
        expectedVersion: Int?,
    ): Int = call {
        api.updateReferenceMax(
            lift = referenceMax.requiredLift().routeValue,
            request = UpdateReferenceMaxRequestDto(
                valueKg = referenceMax.weightKg,
                // The phone's identifier, so the row the server creates is the row the phone
                // already has rather than a second one it will never reconcile.
                id = referenceMax.syncId,
            ),
        ).version
    }

    override suspend fun uploadCycle(
        cycle: SnapshotCycle,
        exercises: List<SnapshotExercise>,
        expectedVersion: Int?,
    ): Int = call {
        // Idempotent by identifier: a repeat returns the cycle that exists rather than a second
        // copy of the same six weeks.
        api.createCycle(cycle.toCreateRequest(exercises)).version
    }

    override suspend fun uploadWorkout(workout: SnapshotWorkout, expectedVersion: Int?): Int =
        call {
            api.createWorkout(workout.toCreateRequest()).version
        }

    // -----------------------------------------------------------------------------------------

    private suspend fun verifyUploaded(snapshot: CloudSnapshot) {
        val state = call { api.applicationState() }

        val missingCycles = snapshot.cycles.map { it.syncId } - state.cycles.map { it.id }.toSet()
        val missingWorkouts =
            snapshot.workouts.map { it.syncId } - state.workouts.map { it.id }.toSet()

        if (missingCycles.isNotEmpty() || missingWorkouts.isNotEmpty()) {
            throw GriffGymError.Server(
                statusCode = 0,
                message = "The backup could not be verified. Your data is still safe on this device.",
            )
        }
    }

    /**
     * Refuses a document this build does not understand.
     *
     * Restoring something half recognised would produce a database that looks like a training
     * history and is not one — far worse than telling the lifter to update the app.
     */
    private fun requireSupportedSchema(state: ApplicationStateResponseDto) {
        if (state.schemaVersion > ApplicationStateResponseDto.SUPPORTED_SCHEMA_VERSION) {
            throw GriffGymError.Server(
                statusCode = 0,
                message = "This backup was made by a newer version of Griff Gym. Update the app to restore it.",
            )
        }
    }

    /**
     * Runs a call and rethrows whatever went wrong as a [GriffGymError].
     *
     * Throwing rather than returning a `Result` because the engine above composes many of these
     * in sequence, and the first failure should stop the sequence — which is what an exception
     * expresses and a chain of `Result` folds does not.
     */
    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: GriffGymError) {
            throw error
        } catch (exception: Exception) {
            throw errorMapper.map(exception)
        }
}
