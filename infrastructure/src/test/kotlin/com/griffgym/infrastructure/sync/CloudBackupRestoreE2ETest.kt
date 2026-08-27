package com.griffgym.infrastructure.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.Rpe
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.TrainingVolume
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutStatus
import com.griffgym.infrastructure.cycleRepository
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.network.ApiErrorMapper
import com.griffgym.infrastructure.network.GriffGymApi
import com.griffgym.infrastructure.repository.RoomReferenceMaxRepository
import com.griffgym.infrastructure.repository.RoomTrainingProgramRepository
import com.griffgym.infrastructure.repository.RoomWorkoutSessionRepository
import com.griffgym.infrastructure.seed.DatabaseSeeder
import com.griffgym.infrastructure.startCycleFrom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The test the whole of Phase 2 exists to keep passing.
 *
 * A lifter trains locally for a while, creates an account, and their history goes up. Then the
 * phone is gone — a new install, an empty database — and they sign in. Does the app come back?
 *
 * Everything real except the server's storage: real Room, real Retrofit, real OkHttp over a
 * loopback socket, real kotlinx.serialization in both directions. That matters, because the
 * failure this is guarding against is not a logic error — it is a field that serialises under
 * one name and deserialises under another, and quietly comes back null. A test with a fake
 * gateway would never see it.
 */
@RunWith(RobolectricTestRunner::class)
class CloudBackupRestoreE2ETest {

    private lateinit var server: MockWebServer
    private lateinit var cloud: InMemoryGriffGymServer
    private lateinit var api: GriffGymApi

    private lateinit var database: GriffGymDatabase
    private lateinit var engine: SyncEngine
    private lateinit var reader: LocalStateReader
    private lateinit var writer: LocalStateWriter
    private lateinit var programRepository: RoomTrainingProgramRepository
    private lateinit var sessionRepository: RoomWorkoutSessionRepository
    private lateinit var referenceMaxRepository: RoomReferenceMaxRepository

    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:30:00Z"), ZoneOffset.UTC)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        cloud = InMemoryGriffGymServer(json)
        server.dispatcher = cloud
        server.start()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GriffGymApi::class.java)

        buildLocalApp()
        trainLocally()
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun `a local lifter's history is backed up and comes back on a new phone`() = runTest {
        val before = reader.read()

        // --- CREATE ACCOUNT: everything on the phone goes up -----------------------------
        engine.backupEverything { }.getOrThrow()

        assertEquals(
            "a completed backup must leave nothing waiting",
            0,
            database.syncMetadataDao().countOutstanding(),
        )

        // --- NEW PHONE: a fresh install, then SIGN IN ------------------------------------
        buildLocalApp()
        assertTrue("the new install should start empty", reader.read().isEmpty)

        engine.restoreEverything().getOrThrow()

        // The state is the same state, compared by the identities the server files it under.
        assertEquals(before, reader.read())
    }

    @Test
    fun `the restored app opens on the same day of the same cycle`() = runTest {
        val expectedTemplate = programRepository.getCurrentWorkoutTemplate()!!

        backupThenRestoreOnAFreshInstall()

        val cycle = database.trainingCycleDao().getCurrent()!!
        assertEquals(1, cycle.cycleNumber)
        assertEquals(210.0, cycle.squatKg, 0.0001)

        val program = programRepository.getActiveProgram()!!
        assertEquals(6, program.weeks.size)
        assertEquals(18, program.workouts.size)
        assertTrue("week six should still be the deload", program.weeks.last().isDeload)

        val current = programRepository.getCurrentWorkoutTemplate()
        assertNotNull("the lifter lost their place in the plan", current)
        assertEquals(expectedTemplate.weekNumber, current!!.weekNumber)
        assertEquals(expectedTemplate.dayNumber, current.dayNumber)
    }

    @Test
    fun `the workout left running mid-session comes back with its sets`() = runTest {
        val expected = sessionRepository.getActiveSession()!!
        val expectedLogged = expected.exercises.flatMap { it.sets }.single { it.completed }

        backupThenRestoreOnAFreshInstall()

        val active = sessionRepository.getActiveSession()

        assertNotNull("the active workout was lost", active)
        assertEquals(WorkoutStatus.IN_PROGRESS, active!!.status)
        assertEquals(expected.dayNumber, active.dayNumber)
        assertNull("a running session has no tonnage to freeze", active.finishedAt)

        val logged = active.exercises.flatMap { it.sets }.single { it.completed }

        assertEquals(expectedLogged.actualWeight, logged.actualWeight)
        assertEquals(expectedLogged.actualReps, logged.actualReps)
        assertEquals(expectedLogged.actualRpe, logged.actualRpe)
        // What was asked for survived the round trip alongside what happened, which is the
        // rule the entire history model rests on.
        assertEquals(expectedLogged.plannedWeight, logged.plannedWeight)
        assertEquals(expectedLogged.plannedReps, logged.plannedReps)
    }

    @Test
    fun `the finished workout comes back finished, with its tonnage`() = runTest {
        val expected = sessionRepository.observeHistory().first().single()

        backupThenRestoreOnAFreshInstall()

        val restored = sessionRepository.observeHistory().first().single()

        assertEquals(WorkoutStatus.COMPLETED, restored.status)
        assertEquals(expected.title, restored.title)
        assertEquals(expected.completedSets, restored.completedSets)
        assertEquals(expected.totalVolume.kilograms, restored.totalVolume.kilograms, 0.0001)
        assertEquals(expected.finishedAt, restored.finishedAt)
    }

    @Test
    fun `reference maxes survive the round trip through real json`() = runTest {
        backupThenRestoreOnAFreshInstall()

        val maxes = referenceMaxRepository.observeReferenceMaxes().first()

        assertEquals(3, maxes.size)
        // Half kilograms are ordinary loads on this program, and a JSON round trip is exactly
        // where one would quietly become 209.99999999999997.
        assertEquals(
            Weight.of(210.0),
            maxes.single { it.category == ExerciseCategory.SQUAT }.weight,
        )
        assertEquals(
            Weight.of(170.0),
            maxes.single { it.category == ExerciseCategory.BENCH_PRESS }.weight,
        )
    }

    @Test
    fun `half kilogram loads and half step rpe survive real serialisation`() = runTest {
        backupThenRestoreOnAFreshInstall()

        val planned = programRepository.getActiveProgram()!!
            .weeks.first().workouts.first().exercises.first().plannedSets

        assertEquals(Weight.of(187.5), planned.first().weight)
        assertEquals(8.0, planned.first().targetRpe!!.min.value, 0.0001)

        val logged = sessionRepository.getActiveSession()!!
            .exercises.flatMap { it.sets }.single { it.completed }

        assertEquals(Rpe.of(8.5), logged.actualRpe)
    }

    @Test
    fun `restoring twice changes nothing`() = runTest {
        backupThenRestoreOnAFreshInstall()
        val once = reader.read()

        engine.restoreEverything().getOrThrow()

        // Idempotent: the second restore wipes and rewrites, and lands in the same place.
        assertEquals(once, reader.read())
        assertEquals(0, database.syncMetadataDao().countOutstanding())
    }

    @Test
    fun `a fresh account reports itself empty rather than conflicting`() = runTest {
        cloud.clear()

        assertTrue(
            "a new account must not look like it already holds a backup",
            engine.isCloudEmpty().getOrThrow(),
        )

        engine.backupEverything { }.getOrThrow()

        assertTrue(
            "an account with training data must not look empty",
            !engine.isCloudEmpty().getOrThrow(),
        )
    }

    // -----------------------------------------------------------------------------------------

    private suspend fun backupThenRestoreOnAFreshInstall() {
        engine.backupEverything { }.getOrThrow()
        buildLocalApp()
        engine.restoreEverything().getOrThrow()
    }

    /** A brand new install: an empty database and everything wired over it. */
    private fun buildLocalApp() {
        if (::database.isInitialized) database.close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GriffGymDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        reader = LocalStateReader(database.cloudSyncDao())
        writer = LocalStateWriter(database, database.cloudSyncDao(), database.syncMetadataDao())

        engine = SyncEngine(
            gateway = RetrofitCloudStateGateway(api, ApiErrorMapper(json)),
            localStateReader = reader,
            localStateWriter = writer,
            syncMetadataDao = database.syncMetadataDao(),
            clock = clock,
        )

        programRepository = RoomTrainingProgramRepository(database.trainingProgramDao())
        sessionRepository = RoomWorkoutSessionRepository(database, database.workoutSessionDao())
        referenceMaxRepository = RoomReferenceMaxRepository(database, database.referenceMaxDao())
    }

    /**
     * Six weeks planned, week 1 day I trained through and completed, day II started and one
     * set logged — a phone put down mid-session, which is the hardest state to restore and the
     * one a lifter would most notice losing.
     */
    private suspend fun trainLocally() {
        DatabaseSeeder(database).seedIfNeeded()
        cycleRepository(database).startCycleFrom(StrengthBlockTemplate.baselineReferenceMaxes, clock)

        val firstTemplate = programRepository.getCurrentWorkoutTemplate()!!
        val firstId = sessionRepository.startSession(
            firstTemplate,
            LocalDate.now(clock),
            clock.instant(),
        )

        sessionRepository.getSession(firstId)!!.exercises.flatMap { it.sets }.forEach { set ->
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
