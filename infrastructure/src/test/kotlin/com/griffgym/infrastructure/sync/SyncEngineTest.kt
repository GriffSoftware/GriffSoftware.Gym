package com.griffgym.infrastructure.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.griffgym.domain.model.GriffGymError
import com.griffgym.domain.model.Rpe
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.TrainingVolume
import com.griffgym.domain.model.Weight
import com.griffgym.infrastructure.cycleRepository
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.entity.SyncEntityType
import com.griffgym.infrastructure.database.entity.SyncState
import com.griffgym.infrastructure.repository.RoomTrainingProgramRepository
import com.griffgym.infrastructure.repository.RoomWorkoutSessionRepository
import com.griffgym.infrastructure.seed.DatabaseSeeder
import com.griffgym.infrastructure.startCycleFrom
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What synchronisation does when the server misbehaves.
 *
 * The happy path is the least interesting thing here. What these tests hold in place is the
 * promise that nothing a server says can cost a lifter their training: a conflict parks the
 * record and keeps the local copy, a dropped connection leaves everything pending, and a
 * backup that did not finish is never recorded as one that did.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private lateinit var database: GriffGymDatabase
    private lateinit var gateway: FakeCloudStateGateway
    private lateinit var engine: SyncEngine
    private lateinit var sessionRepository: RoomWorkoutSessionRepository
    private lateinit var programRepository: RoomTrainingProgramRepository

    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:30:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GriffGymDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        gateway = FakeCloudStateGateway()
        engine = SyncEngine(
            gateway = gateway,
            localStateReader = LocalStateReader(database.cloudSyncDao()),
            localStateWriter = LocalStateWriter(
                database,
                database.cloudSyncDao(),
                database.syncMetadataDao(),
            ),
            syncMetadataDao = database.syncMetadataDao(),
            clock = clock,
        )

        programRepository = RoomTrainingProgramRepository(database.trainingProgramDao())
        sessionRepository = RoomWorkoutSessionRepository(database, database.workoutSessionDao())

        DatabaseSeeder(database).seedIfNeeded()
        cycleRepository(database).startCycleFrom(StrengthBlockTemplate.baselineReferenceMaxes, clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a completed backup marks everything synced and records when it finished`() = runTest {
        engine.backupEverything { }.getOrThrow()

        assertEquals(0, database.syncMetadataDao().countOutstanding())

        val cycleSyncId = database.trainingCycleDao().getCurrent()!!.syncId
        val metadata = database.syncMetadataDao().get(SyncEntityType.TRAINING_CYCLE, cycleSyncId)

        assertNotNull(metadata)
        assertEquals(SyncState.SYNCED, metadata!!.syncState)
        assertEquals(clock.instant().toEpochMilli(), metadata.lastSyncedAtUtc)
    }

    @Test
    fun `a backup that failed is never recorded as one that succeeded`() = runTest {
        // The worst outcome this class could produce: somebody who stops worrying about a copy
        // that does not exist.
        gateway.uploadSnapshotFailure = GriffGymError.Network()

        val result = engine.backupEverything { }

        assertTrue(result.isFailure)
        assertEquals(
            "a failed upload must leave nothing marked as backed up",
            0,
            database.syncMetadataDao().getOutstanding().count { it.syncState == SyncState.SYNCED },
        )
    }

    @Test
    fun `a version conflict parks the record and keeps the local copy`() = runTest {
        val sessionSyncId = logAWorkout()
        engine.markPending(SyncEntityType.WORKOUT_SESSION, sessionSyncId)

        gateway.failures[sessionSyncId] = GriffGymError.VersionConflict(
            expectedVersion = 3,
            actualVersion = 7,
        )

        engine.pushPendingChanges().getOrThrow()

        val metadata = database.syncMetadataDao()
            .get(SyncEntityType.WORKOUT_SESSION, sessionSyncId)!!

        assertEquals(SyncState.CONFLICT, metadata.syncState)
        // The version the server actually holds, so a future resolution knows what it is up
        // against rather than guessing.
        assertEquals(7, metadata.serverVersion)

        // And the workout is still exactly where the lifter left it.
        val session = sessionRepository.getActiveSession()
        assertNotNull(session)
        assertTrue(session!!.exercises.flatMap { it.sets }.any { it.completed })
    }

    @Test
    fun `a record in conflict is not quietly downgraded back to pending`() = runTest {
        val sessionSyncId = logAWorkout()
        engine.markPending(SyncEntityType.WORKOUT_SESSION, sessionSyncId)
        gateway.failures[sessionSyncId] = GriffGymError.Conflict()
        engine.pushPendingChanges().getOrThrow()

        // The lifter logs another set into the same workout.
        engine.markPending(SyncEntityType.WORKOUT_SESSION, sessionSyncId)

        // Marking it pending again would send the local version and overwrite whatever the
        // server had — which is the silent data loss this whole mechanism exists to prevent.
        assertEquals(
            SyncState.CONFLICT,
            database.syncMetadataDao().get(SyncEntityType.WORKOUT_SESSION, sessionSyncId)!!.syncState,
        )
    }

    @Test
    fun `a conflicted record is not retried on the next pass`() = runTest {
        val sessionSyncId = logAWorkout()
        engine.markPending(SyncEntityType.WORKOUT_SESSION, sessionSyncId)
        gateway.failures[sessionSyncId] = GriffGymError.VersionConflict(1, 2)
        engine.pushPendingChanges().getOrThrow()

        gateway.failures.clear()
        gateway.uploadedWorkoutIds.clear()

        engine.pushPendingChanges().getOrThrow()

        assertFalse(
            "a parked record was re-sent, overwriting the server",
            gateway.uploadedWorkoutIds.contains(sessionSyncId),
        )
    }

    @Test
    fun `a dropped connection leaves everything pending for the next attempt`() = runTest {
        val sessionSyncId = logAWorkout()
        engine.markPending(SyncEntityType.WORKOUT_SESSION, sessionSyncId)
        gateway.failures[sessionSyncId] = GriffGymError.Network()

        val result = engine.pushPendingChanges()

        assertTrue(result.isFailure)

        val metadata = database.syncMetadataDao()
            .get(SyncEntityType.WORKOUT_SESSION, sessionSyncId)!!

        // FAILED, not CONFLICT: waiting could fix this, and the next pass should try again.
        assertEquals(SyncState.FAILED, metadata.syncState)
        assertEquals(1, database.syncMetadataDao().countOutstanding())
    }

    @Test
    fun `nothing outstanding means nothing is sent`() = runTest {
        engine.backupEverything { }.getOrThrow()
        gateway.uploadedWorkoutIds.clear()
        gateway.uploadExerciseCalls = 0

        val accepted = engine.pushPendingChanges().getOrThrow()

        assertEquals(0, accepted)
        assertEquals(0, gateway.uploadExerciseCalls)
    }

    @Test
    fun `the movement catalogue is sent before anything that references it`() = runTest {
        val sessionSyncId = logAWorkout()
        engine.markPending(SyncEntityType.WORKOUT_SESSION, sessionSyncId)

        engine.pushPendingChanges().getOrThrow()

        // A log referencing a movement the server has never heard of would be rejected, and
        // the rejection would look like a conflict rather than an ordering mistake.
        assertEquals(1, gateway.uploadExerciseCalls)
        assertTrue(gateway.uploadedWorkoutIds.contains(sessionSyncId))
    }

    @Test
    fun `a restore replaces the local database with the account's copy`() = runTest {
        engine.backupEverything { }.getOrThrow()
        val uploaded = gateway.cloudState

        LocalStateWriter(database, database.cloudSyncDao(), database.syncMetadataDao())
            .clearLocalTrainingData()

        engine.restoreEverything().getOrThrow()

        assertEquals(uploaded, LocalStateReader(database.cloudSyncDao()).read())
        assertNotNull(programRepository.getCurrentWorkoutTemplate())
    }

    @Test
    fun `a restore that fails mid-flight leaves the database untouched`() = runTest {
        val before = LocalStateReader(database.cloudSyncDao()).read()
        gateway.fetchFailure = GriffGymError.Network()

        val result = engine.restoreEverything()

        assertTrue(result.isFailure)
        assertEquals(before, LocalStateReader(database.cloudSyncDao()).read())
    }

    // -----------------------------------------------------------------------------------------

    /** Starts week 1 day I and logs one set into it, then returns the session's sync id. */
    private suspend fun logAWorkout(): String {
        val template = programRepository.getCurrentWorkoutTemplate()!!
        val sessionId = sessionRepository.startSession(
            template,
            LocalDate.now(clock),
            clock.instant(),
        )

        val set = sessionRepository.getSession(sessionId)!!.exercises.first().sets.first()
        sessionRepository.updateSet(
            set.id,
            SetResult(Weight.of(190.0), 3, Rpe.of(8.5), completed = true, notes = null),
        )

        return database.workoutSessionDao().syncIdOf(sessionId)!!
    }

    @Suppress("unused")
    private val unusedVolume = TrainingVolume.ZERO
}
