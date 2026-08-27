package com.griffgym.infrastructure.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.griffgym.domain.model.Rpe
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.Weight
import com.griffgym.infrastructure.cycleRepository
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.repository.RoomTrainingProgramRepository
import com.griffgym.infrastructure.repository.RoomWorkoutSessionRepository
import com.griffgym.infrastructure.seed.DatabaseSeeder
import com.griffgym.infrastructure.startCycleFrom
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Restore scenarios that [CloudStateRoundTripTest] does not cover: a snapshot that is
 * internally *inconsistent* rather than merely malformed enough to trip a unique index.
 *
 * A workout naming a template the snapshot does not contain, and an exercise log naming a
 * movement the snapshot's catalogue does not contain, are both things a lifter can genuinely
 * end up with — a template deleted from a later plan revision before this snapshot was taken,
 * a movement renamed and re-keyed server-side. Neither should turn "restore my account" into
 * "lose a workout".
 */
@RunWith(RobolectricTestRunner::class)
class LocalStateWriterRestoreEdgeCasesTest {

    private lateinit var database: GriffGymDatabase
    private lateinit var reader: LocalStateReader
    private lateinit var writer: LocalStateWriter
    private lateinit var sessionRepository: RoomWorkoutSessionRepository
    private lateinit var programRepository: RoomTrainingProgramRepository

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

        DatabaseSeeder(database).seedIfNeeded()
        cycleRepository(database).startCycleFrom(StrengthBlockTemplate.baselineReferenceMaxes, clock)

        val template = programRepository.getCurrentWorkoutTemplate()!!
        val sessionId = sessionRepository.startSession(template, LocalDate.now(clock), clock.instant())
        val set = sessionRepository.getSession(sessionId)!!.exercises.first().sets.first()
        sessionRepository.updateSet(
            set.id,
            SetResult(Weight.of(190.0), 3, Rpe.of(8.5), completed = true, notes = "top set"),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a workout naming a template absent from the snapshot restores untemplated, not dropped`() =
        runTest {
            val before = reader.read()
            val workout = before.workouts.single()
            assertNotNull("test setup expected a template link to remove", workout.templateSyncId)

            val corrupt = before.copy(
                workouts = listOf(workout.copy(templateSyncId = "template-not-in-snapshot")),
            )

            writer.clearLocalTrainingData()
            writer.replaceLocalState(corrupt, restoredAt)

            val restoredSessions = database.cloudSyncDao().allWorkoutSessions()
            assertEquals(1, restoredSessions.size)

            val restored = restoredSessions.single()
            // Provenance is dropped rather than the whole session: the row-id foreign key
            // cannot possibly resolve to a template this snapshot never mentioned.
            assertNull("a dangling template reference should not survive as a row id", restored.templateId)

            // Everything the session itself carries — the actual training that happened — is
            // untouched by the missing provenance link.
            assertEquals(workout.weekNumber, restored.weekNumber)
            assertEquals(workout.dayNumber, restored.dayNumber)
            assertEquals(workout.title, restored.title)
            assertEquals(workout.status, restored.status)

            val active = sessionRepository.getActiveSession()
            assertNotNull("the workout itself must not be lost", active)
            val loggedSet = active!!.exercises.flatMap { it.sets }.single { it.completed }
            assertEquals(Weight.of(190.0), loggedSet.actualWeight)
        }

    @Test
    fun `an exercise log naming a movement absent from the catalogue recreates it rather than losing the set`() =
        runTest {
            val before = reader.read()
            val log = before.workouts.single().exercises.first { it.sets.any { set -> set.completed } }
            val missingExerciseSyncId = log.exerciseSyncId
            assertNotNull(missingExerciseSyncId)

            // The catalogue no longer mentions this movement, but the log — the record of what
            // was actually done — still names it, exactly as the API would send back a workout
            // whose movement was renamed and re-keyed after this workout was logged.
            val corrupt = before.copy(
                exercises = before.exercises.filterNot { it.syncId == missingExerciseSyncId },
            )

            writer.clearLocalTrainingData()
            writer.replaceLocalState(corrupt, restoredAt)

            // The movement comes back — recreated from the log's own snapshot of its name and
            // category — under the identity the log already pointed at.
            val recreated = database.cloudSyncDao().allExercises()
                .singleOrNull { it.syncId == missingExerciseSyncId }
            assertNotNull("the missing movement should be recreated, not silently dropped", recreated)
            assertEquals(log.exerciseName, recreated!!.name)

            // And the set logged against it is intact, not orphaned or discarded.
            val active = sessionRepository.getActiveSession()
            assertNotNull(active)
            val loggedSet = active!!.exercises.flatMap { it.sets }.single { it.completed }
            assertEquals(Weight.of(190.0), loggedSet.actualWeight)
            assertEquals(3, loggedSet.actualReps)
            assertNotEquals(0L, recreated.id)
        }
}
