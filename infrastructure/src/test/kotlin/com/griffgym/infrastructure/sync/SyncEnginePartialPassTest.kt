package com.griffgym.infrastructure.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.griffgym.domain.model.GriffGymError
import com.griffgym.domain.model.Rpe
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.StrengthBlockTemplate
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
 * What a pass does when the server accepts some records and then the connection drops.
 *
 * `pushPendingChanges` reports the whole pass as one [Result], but each record's metadata is
 * written the moment the server accepts it — separately, and before anything about the rest of
 * the pass is known. This is the case that would tell them apart: does a record already marked
 * SYNCED stay that way when a sibling in the same pass fails outright afterwards, or does the
 * pass-level failure somehow take it back down with it?
 */
@RunWith(RobolectricTestRunner::class)
class SyncEnginePartialPassTest {

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
    fun `a record accepted earlier in a pass stays SYNCED even though a sibling fails afterwards`() =
        runTest {
            val cycleSyncId = database.trainingCycleDao().getCurrent()!!.syncId
            val sessionSyncId = logAWorkout()

            engine.markPending(SyncEntityType.TRAINING_CYCLE, cycleSyncId)
            engine.markPending(SyncEntityType.WORKOUT_SESSION, sessionSyncId)

            // Only the workout drops; the cycle upload is left free to succeed, whichever order
            // the pass happens to process the two outstanding records in.
            gateway.failures[sessionSyncId] = GriffGymError.Network()

            val result = engine.pushPendingChanges()

            assertTrue("a network failure on one record must fail the pass", result.isFailure)

            val cycleMetadata = database.syncMetadataDao()
                .get(SyncEntityType.TRAINING_CYCLE, cycleSyncId)!!
            val sessionMetadata = database.syncMetadataDao()
                .get(SyncEntityType.WORKOUT_SESSION, sessionSyncId)!!

            assertEquals(
                "the accepted record must not be rolled back because a later one failed",
                SyncState.SYNCED,
                cycleMetadata.syncState,
            )
            assertNotNull(cycleMetadata.lastSyncedAtUtc)
            assertEquals(SyncState.FAILED, sessionMetadata.syncState)

            // The server truly has the cycle: a second pass must not re-send it.
            gateway.uploadedCycleIds.clear()
            gateway.failures.clear()
            engine.pushPendingChanges().getOrThrow()
            assertTrue(
                "a record already SYNCED must not be re-uploaded on the next pass",
                gateway.uploadedCycleIds.isEmpty(),
            )
        }

    // -----------------------------------------------------------------------------------------

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
}
