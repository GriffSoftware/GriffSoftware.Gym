package com.griffgym.infrastructure

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.Rpe
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.TrainingVolume
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutStatus
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.entity.SyncState
import com.griffgym.infrastructure.repository.RoomReferenceMaxRepository
import com.griffgym.infrastructure.repository.RoomTrainingProgramRepository
import com.griffgym.infrastructure.repository.RoomWorkoutSessionRepository
import com.griffgym.infrastructure.seed.DatabaseSeeder
import com.griffgym.infrastructure.sync.LocalStateReader
import com.griffgym.infrastructure.sync.LocalStateWriter
import com.griffgym.infrastructure.sync.model.CloudSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * The test the whole backup feature exists to keep passing.
 *
 * It builds the state a real lifter would have — a cycle, its full six-week plan, a finished
 * workout with logged sets, and a second workout left running mid-session — reads it out the
 * way a backup does, wipes the database the way a fresh install would be, writes it back the
 * way a restore does, and then asks whether the app is the same app.
 *
 * "The same" is checked twice over: the snapshot must come back byte-for-byte identical, and
 * the repositories the UI actually reads from must return the same training data. The second
 * check matters because a restore could satisfy the first while wiring the rows together
 * wrongly, and the lifter would open the app to a plan that pointed at the wrong day.
 */
@RunWith(RobolectricTestRunner::class)
class CloudStateRoundTripTest {

    private lateinit var database: GriffGymDatabase
    private lateinit var reader: LocalStateReader
    private lateinit var writer: LocalStateWriter
    private lateinit var programRepository: RoomTrainingProgramRepository
    private lateinit var sessionRepository: RoomWorkoutSessionRepository
    private lateinit var referenceMaxRepository: RoomReferenceMaxRepository

    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:30:00Z"), ZoneOffset.UTC)
    private val restoredAt = Instant.parse("2026-03-05T08:00:00Z")

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GriffGymDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        reader = LocalStateReader(database.cloudSyncDao())
        writer = LocalStateWriter(database, database.cloudSyncDao(), database.syncMetadataDao())

        programRepository = RoomTrainingProgramRepository(database.trainingProgramDao())
        sessionRepository = RoomWorkoutSessionRepository(database, database.workoutSessionDao())
        referenceMaxRepository = RoomReferenceMaxRepository(database, database.referenceMaxDao())

        DatabaseSeeder(database).seedIfNeeded()
        cycleRepository(database).startCycleFrom(StrengthBlockTemplate.baselineReferenceMaxes, clock)

        trainOneWorkoutAndStartAnother()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a lifter's whole state survives being read out, wiped and written back`() = runTest {
        val before = reader.read()

        assertFalse("nothing was set up to restore", before.isEmpty)

        writer.clearLocalTrainingData()
        assertTrue("the wipe left data behind", reader.read().isEmpty)

        writer.replaceLocalState(before, restoredAt)

        // Identity is by sync id throughout, so this is a genuine comparison and not one that
        // passes because both sides were renumbered the same way.
        assertEquals(before, reader.read())
    }

    @Test
    fun `the restored cycle keeps its number and the maxes it was built from`() = runTest {
        val before = reader.read()
        val originalCycle = before.cycles.single()

        restore(before)

        val cycle = database.trainingCycleDao().getCurrent()!!

        assertEquals(1, cycle.cycleNumber)
        assertEquals(CycleStatus.ACTIVE, cycle.status)
        // Frozen, not recalculated. Rebuilding a historical block from today's numbers would
        // rewrite what the lifter actually trained.
        assertEquals(originalCycle.squatKg, cycle.squatKg, 0.0001)
        assertEquals(originalCycle.benchPressKg, cycle.benchPressKg, 0.0001)
        assertEquals(originalCycle.deadliftKg, cycle.deadliftKg, 0.0001)
        assertEquals(originalCycle.syncId, cycle.syncId)
    }

    @Test
    fun `the restored program is the plan that was stored, not one regenerated from maxes`() =
        runTest {
            val before = reader.read()

            restore(before)

            val program = programRepository.getActiveProgram()!!

            assertEquals(6, program.weeks.size)
            assertEquals(18, program.workouts.size)
            assertTrue("week six should still be the deload", program.weeks.last().isDeload)

            val top = program.weeks.first().workouts.first().exercises.first()
            assertEquals("Przysiad", top.exercise.name)
            assertEquals("1x3x187.5kg", top.scheme!!.format())
        }

    @Test
    fun `the lifter carries on from the day they were on`() = runTest {
        val expected = programRepository.getCurrentWorkoutTemplate()!!

        restore(reader.read())

        val actual = programRepository.getCurrentWorkoutTemplate()

        assertNotNull("the progress pointer was lost", actual)
        assertEquals(expected.weekNumber, actual!!.weekNumber)
        assertEquals(expected.dayNumber, actual.dayNumber)
        assertEquals(expected.title, actual.title)
    }

    @Test
    fun `the workout that was still running comes back mid-session, with its logged sets`() =
        runTest {
            // Day II opens on the deadlift, not the squat, so what the plan prescribed is read
            // from the session rather than assumed — the point is that it survives, not what it
            // happens to be.
            val plannedBefore = sessionRepository.getActiveSession()!!
                .exercises.first().sets.first().plannedWeight

            restore(reader.read())

            val active = sessionRepository.getActiveSession()

            assertNotNull("the active workout was lost", active)
            assertEquals(WorkoutStatus.IN_PROGRESS, active!!.status)
            assertEquals(2, active.dayNumber)

            val logged = active.exercises.flatMap { it.sets }.filter { it.completed }
            assertEquals(1, logged.size)

            val set = logged.single()
            assertEquals(Weight.of(190.0), set.actualWeight)
            assertEquals(3, set.actualReps)
            assertEquals(Rpe.of(8.5), set.actualRpe)

            // Planned and actual are two separate facts and neither was derived from the other:
            // the plan came back untouched even though the lifter logged something else.
            assertEquals(plannedBefore, set.plannedWeight)
            assertNotEquals(set.plannedWeight, set.actualWeight)
            assertEquals(3, set.plannedReps)
        }

    @Test
    fun `the finished workout stays finished, with its tonnage and its history`() = runTest {
        val expected = sessionRepository.observeHistoryOnce().single()

        restore(reader.read())

        val restored = sessionRepository.observeHistoryOnce().single()

        assertEquals(WorkoutStatus.COMPLETED, restored.status)
        assertEquals(expected.title, restored.title)
        assertEquals(expected.totalVolume.kilograms, restored.totalVolume.kilograms, 0.0001)
        assertEquals(expected.completedSets, restored.completedSets)
        assertEquals(expected.finishedAt, restored.finishedAt)
    }

    @Test
    fun `reference maxes come back for all three lifts`() = runTest {
        restore(reader.read())

        val maxes = referenceMaxRepository.observeReferenceMaxesOnce()

        assertEquals(3, maxes.size)
        assertEquals(
            Weight.of(210.0),
            maxes.single { it.category == ExerciseCategory.SQUAT }.weight,
        )
    }

    @Test
    fun `a restore leaves nothing waiting to be uploaded`() = runTest {
        restore(reader.read())

        // The database was just built from the server's copy. Marking any of it as pending
        // would send the whole history straight back up, and would show the lifter a phone
        // full of unsynced changes the moment they opened it.
        assertEquals(0, database.syncMetadataDao().countOutstanding())

        val cycleSyncId = reader.read().cycles.single().syncId
        val metadata = database.syncMetadataDao()
            .get(com.griffgym.infrastructure.database.entity.SyncEntityType.TRAINING_CYCLE, cycleSyncId)

        assertNotNull(metadata)
        assertEquals(SyncState.SYNCED, metadata!!.syncState)
        assertEquals(restoredAt.toEpochMilli(), metadata.lastSyncedAtUtc)
    }

    @Test
    fun `a failed restore leaves the database exactly as it was`() = runTest {
        val before = reader.read()

        // Two cycles claiming to be cycle 1. The unique index rejects the second, which must
        // take the whole restore down rather than leaving half a history behind.
        val corrupt = before.copy(cycles = before.cycles + before.cycles.single().copy(
            syncId = "duplicate-cycle-number",
        ))

        runCatching { writer.replaceLocalState(corrupt, restoredAt) }
            .onSuccess { error("the corrupt snapshot should not have been accepted") }

        assertEquals("the failed restore was not rolled back", before, reader.read())
        assertNotNull(sessionRepository.getActiveSession())
    }

    @Test
    fun `an empty snapshot restores as an empty app rather than failing`() = runTest {
        writer.replaceLocalState(CloudSnapshot.Empty, restoredAt)

        assertTrue(reader.read().isEmpty)
        assertNull(sessionRepository.getActiveSession())
    }

    // -----------------------------------------------------------------------------------------

    private suspend fun restore(snapshot: CloudSnapshot) {
        writer.clearLocalTrainingData()
        writer.replaceLocalState(snapshot, restoredAt)
    }

    /**
     * Week 1 day I trained through and completed, then day II started and one set logged —
     * a phone put down mid-session, which is the state a restore has to be able to reproduce.
     */
    private suspend fun trainOneWorkoutAndStartAnother() {
        val firstTemplate = programRepository.getCurrentWorkoutTemplate()!!
        val firstId = sessionRepository.startSession(
            firstTemplate,
            LocalDate.now(clock),
            clock.instant(),
        )

        val firstSession = sessionRepository.getSession(firstId)!!
        firstSession.exercises.flatMap { it.sets }.forEach { set ->
            sessionRepository.updateSet(
                set.id,
                SetResult(
                    weight = set.plannedWeight ?: Weight.of(100.0),
                    reps = set.plannedReps ?: 5,
                    rpe = Rpe.of(8.0),
                    completed = true,
                    notes = null,
                ),
            )
        }
        // The tonnage the app itself would write: derived from the sets that were actually
        // logged, not a number invented by the test. The server recomputes it on completion,
        // so a made-up value here would come back corrected and look like a restore bug.
        sessionRepository.completeSession(
            firstId,
            clock.instant().plusSeconds(3600),
            sessionRepository.getSession(firstId)!!.totalVolume,
        )
        programRepository.setCurrentWorkoutTemplate(
            programRepository.getWorkoutTemplateAfter(firstTemplate.sequenceNumber)!!.id,
        )

        val secondTemplate = programRepository.getCurrentWorkoutTemplate()!!
        val secondId = sessionRepository.startSession(
            secondTemplate,
            LocalDate.now(clock).plusDays(2),
            clock.instant().plusSeconds(172_800),
        )
        val openSet = sessionRepository.getSession(secondId)!!.exercises.first().sets.first()
        sessionRepository.updateSet(
            openSet.id,
            SetResult(Weight.of(190.0), 3, Rpe.of(8.5), completed = true, notes = "felt fast"),
        )
    }
}

private suspend fun RoomWorkoutSessionRepository.observeHistoryOnce() = observeHistory().first()

private suspend fun RoomReferenceMaxRepository.observeReferenceMaxesOnce() =
    observeReferenceMaxes().first()
