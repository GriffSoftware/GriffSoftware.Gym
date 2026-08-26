package com.griffgym.infrastructure

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.Weight
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao
import com.griffgym.infrastructure.database.entity.ReferenceMaxEntity
import com.griffgym.infrastructure.repository.RoomReferenceMaxRepository
import com.griffgym.infrastructure.repository.RoomTrainingProgramRepository
import com.griffgym.infrastructure.repository.RoomWorkoutSessionRepository
import com.griffgym.infrastructure.seed.DatabaseSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * What a brand new installation looks like, and what starting a cycle writes into it.
 *
 * Runs against the real schema so the transaction, the foreign keys and the mappers are all
 * exercised rather than assumed. A cycle and its plan are one unit of work here, exactly as
 * they are in the app: there is no way to create a program without one.
 */
@RunWith(RobolectricTestRunner::class)
class ProgramCreationTest {

    private lateinit var database: GriffGymDatabase
    private lateinit var programRepository: RoomTrainingProgramRepository
    private lateinit var referenceMaxRepository: RoomReferenceMaxRepository

    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:30:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.now(clock)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GriffGymDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        programRepository = RoomTrainingProgramRepository(database.trainingProgramDao())
        referenceMaxRepository = RoomReferenceMaxRepository(database.referenceMaxDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `seeding a fresh database gives it a catalogue and nothing else`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()

        assertEquals(12, database.exerciseDao().count())
        // The lifter's own numbers are theirs to enter, not ours to invent.
        assertFalse(programRepository.hasProgram())
        assertFalse(referenceMaxRepository.hasAnyReferenceMax())
        assertFalse(
            RoomWorkoutSessionRepository(database, database.workoutSessionDao()).hasAnySession(),
        )
        assertNull(programRepository.getActiveProgram())
        assertNull(programRepository.getCurrentWorkoutTemplate())
        assertNull(cycleRepository(database).getCurrentCycle())
    }

    @Test
    fun `starting a cycle writes the whole block and aims progress at its first unit`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()

        createBaselineCycle()

        assertTrue(programRepository.hasProgram())
        val program = programRepository.getActiveProgram()!!
        assertEquals(6, program.weeks.size)
        assertEquals(18, program.workouts.size)

        val current = programRepository.getCurrentWorkoutTemplate()!!
        assertEquals(1, current.weekNumber)
        assertEquals(1, current.dayNumber)
        assertEquals("1x3x187.5kg", current.exercises.first().scheme!!.format())
    }

    @Test
    fun `the first cycle is cycle one, active, and owns the program`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()

        val cycle = createBaselineCycle()

        assertEquals(1, cycle.cycleNumber)
        assertEquals(CycleStatus.ACTIVE, cycle.status)
        assertNull(cycle.completedAt)
        assertEquals(clock.instant(), cycle.startedAt)
        assertNotNull(cycleRepository(database).getCycleProgram(cycle.id))
    }

    @Test
    fun `the cycle snapshots the maxes its block was calculated from`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()

        val cycle = createBaselineCycle(maxes = ownMaxes)

        assertEquals(ReferenceMaxSnapshot.of(ownMaxes), cycle.referenceMaxes)
    }

    @Test
    fun `week progress is counted from the plan before anything is logged`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()
        val cycle = createBaselineCycle()

        val summary = cycleRepository(database).getCycleSummary(cycle.id)!!

        assertEquals(6, summary.weekCount)
        assertEquals(18, summary.plannedWorkouts)
        assertEquals(0, summary.completedWorkouts)
        assertEquals(1, summary.currentWeekNumber)
        assertTrue(summary.weeks.last().isDeload)
    }

    @Test
    fun `sequence numbers run through the block in training order`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()
        createBaselineCycle()

        val program = programRepository.getActiveProgram()!!
        assertEquals((1..18).toList(), program.workouts.map { it.sequenceNumber })
        assertEquals(
            List(6) { listOf(1, 2, 3) }.flatten(),
            program.workouts.map { it.dayNumber },
        )
    }

    @Test
    fun `a cycle can be started before the catalogue has been seeded`() = runTest {
        // Seeding runs off the main thread at launch; setup must not depend on it winning.
        createBaselineCycle()

        assertEquals(12, database.exerciseDao().count())
        assertNotNull(programRepository.getActiveProgram())
        assertEquals(
            ExerciseCategory.SQUAT,
            database.exerciseDao().getByName("Przysiad")!!.category,
        )
    }

    @Test
    fun `seeding after a cycle was started does not duplicate the catalogue`() = runTest {
        createBaselineCycle()

        DatabaseSeeder(database).seedIfNeeded()

        assertEquals(12, database.exerciseDao().count())
    }

    @Test
    fun `a lifter's own maxes produce their own loads`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()

        createBaselineCycle(maxes = ownMaxes)

        val current = programRepository.getCurrentWorkoutTemplate()!!
        assertEquals("1x3x160kg", current.exercises.first().scheme!!.format())
        assertEquals(ExerciseType.TOP, current.exercises.first().type)
    }

    @Test
    fun `the deload week is half of the maxes the cycle was built on`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()
        createBaselineCycle(maxes = ownMaxes)

        val deloadDay = programRepository.getActiveProgram()!!.weeks.last().workouts.first()

        // SQ 180 -> 90, BP 140 -> 70. One number for the whole week.
        assertEquals("3x3x90kg", deloadDay.exercises[0].scheme!!.format())
        assertEquals("3x5x70kg", deloadDay.exercises[1].scheme!!.format())
    }

    @Test
    fun `the block and the maxes it was calculated from land together`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()

        createBaselineCycle(maxes = ownMaxes)

        assertTrue(programRepository.hasProgram())
        assertEquals(
            ownMaxes,
            referenceMaxRepository.observeReferenceMaxes().first()
                .associate { it.category to it.weight },
        )
        assertEquals(today, referenceMaxRepository.getReferenceMax(ExerciseCategory.SQUAT)!!.updatedOn)
    }

    @Test
    fun `a max that cannot be written rolls the whole cycle back`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()
        // Persistence gives out after the plan has been written but before the maxes have:
        // the window that used to leave a program behind with no numbers to go with it.
        val repository = cycleRepository(database, FailingReferenceMaxDao(database.referenceMaxDao()))

        val failure = runCatching { repository.startCycleFrom(ownMaxes, clock) }

        assertTrue(failure.isFailure)
        assertFalse(programRepository.hasProgram())
        assertNull(programRepository.getActiveProgram())
        assertNull(programRepository.getCurrentWorkoutTemplate())
        assertNull(cycleRepository(database).getCurrentCycle())
        assertFalse(referenceMaxRepository.hasAnyReferenceMax())
        assertEquals(0, database.trainingProgramDao().programCount())
    }

    @Test
    fun `starting cycle two stands the old plan down and leaves cycle one's record alone`() =
        runTest {
            DatabaseSeeder(database).seedIfNeeded()
            val repository = cycleRepository(database)
            val first = repository.startCycleFrom(StrengthBlockTemplate.baselineReferenceMaxes, clock)
            repository.completeCurrentCycle(clock.instant())

            val second = repository.startCycleFrom(
                maxes = ownMaxes,
                at = Instant.parse("2026-04-15T09:30:00Z"),
                date = LocalDate.of(2026, 4, 15),
            )

            assertEquals(2, second.cycleNumber)
            assertEquals(2, database.trainingProgramDao().programCount())
            // Exactly one active plan, and it is the new one.
            assertEquals(
                second.id,
                database.trainingProgramDao().getActiveProgramRow()!!.cycleId,
            )

            val storedFirst = repository.getCycle(first.id)!!
            assertEquals(CycleStatus.COMPLETED, storedFirst.status)
            assertEquals(
                ReferenceMaxSnapshot.of(StrengthBlockTemplate.baselineReferenceMaxes),
                storedFirst.referenceMaxes,
            )
            // And the live table now holds the new cycle's numbers.
            assertEquals(
                Weight.of(180.0),
                referenceMaxRepository.getReferenceMax(ExerciseCategory.SQUAT)!!.weight,
            )
        }

    @Test
    fun `cycles come back newest first with their own progress`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()
        val repository = cycleRepository(database)
        repository.startCycleFrom(StrengthBlockTemplate.baselineReferenceMaxes, clock)
        repository.completeCurrentCycle(clock.instant())
        repository.startCycleFrom(
            maxes = ownMaxes,
            at = Instant.parse("2026-04-15T09:30:00Z"),
            date = LocalDate.of(2026, 4, 15),
        )

        val summaries = repository.observeCycleSummaries().first()

        assertEquals(listOf(2, 1), summaries.map { it.cycle.cycleNumber })
        assertTrue(summaries.all { it.plannedWorkouts == 18 })
        assertEquals(listOf(0, 0), summaries.map { it.completedWorkouts })
    }

    @Test
    fun `deleting a cycle takes its plan with it and leaves the other alone`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()
        val repository = cycleRepository(database)
        val first = repository.startCycleFrom(StrengthBlockTemplate.baselineReferenceMaxes, clock)
        repository.completeCurrentCycle(clock.instant())
        repository.startCycleFrom(
            maxes = ownMaxes,
            at = Instant.parse("2026-04-15T09:30:00Z"),
            date = LocalDate.of(2026, 4, 15),
        )

        database.compileStatement("DELETE FROM training_cycle WHERE id = ${first.id}")
            .executeUpdateDelete()

        assertNull(repository.getCycleProgram(first.id))
        assertEquals(1, database.trainingProgramDao().programCount())
        assertNotNull(programRepository.getActiveProgram())
    }

    private suspend fun createBaselineCycle(
        maxes: Map<ExerciseCategory, Weight> = StrengthBlockTemplate.baselineReferenceMaxes,
    ) = cycleRepository(database).startCycleFrom(maxes, clock)

    /** Lets a test blow the transaction up at the point the maxes are written. */
    private class FailingReferenceMaxDao(
        delegate: ReferenceMaxDao,
    ) : ReferenceMaxDao by delegate {
        override suspend fun upsertAll(referenceMaxes: List<ReferenceMaxEntity>): Unit =
            throw IllegalStateException("could not write the reference maxes")
    }

    private companion object {
        val ownMaxes = mapOf(
            ExerciseCategory.SQUAT to Weight.of(180.0),
            ExerciseCategory.BENCH_PRESS to Weight.of(140.0),
            ExerciseCategory.DEADLIFT to Weight.of(200.0),
        )
    }
}
