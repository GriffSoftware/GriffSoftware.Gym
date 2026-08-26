package com.griffgym.infrastructure

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.Rpe
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.TrainingVolume
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutStatus
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.repository.RoomReferenceMaxRepository
import com.griffgym.infrastructure.repository.RoomTrainingProgramRepository
import com.griffgym.infrastructure.repository.RoomWorkoutSessionRepository
import com.griffgym.infrastructure.seed.DatabaseSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Exercises the real Room schema, mappers and repositories against an in-memory database.
 */
@RunWith(RobolectricTestRunner::class)
class GriffGymDatabaseTest {

    private lateinit var database: GriffGymDatabase
    private lateinit var programRepository: RoomTrainingProgramRepository
    private lateinit var sessionRepository: RoomWorkoutSessionRepository
    private lateinit var referenceMaxRepository: RoomReferenceMaxRepository

    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:30:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GriffGymDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        programRepository = RoomTrainingProgramRepository(database.trainingProgramDao())
        sessionRepository = RoomWorkoutSessionRepository(database, database.workoutSessionDao())
        referenceMaxRepository = RoomReferenceMaxRepository(database.referenceMaxDao())

        DatabaseSeeder(database).seedIfNeeded()
        // What first-run setup does on a fresh install: cycle 1, and the block that belongs
        // to it. The seeder no longer invents a plan.
        cycleRepository(database).startCycleFrom(StrengthBlockTemplate.baselineReferenceMaxes, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `persists the six week program with eighteen units`() = runTest {
        val program = programRepository.getActiveProgram()!!

        assertEquals(6, program.weeks.size)
        assertEquals(18, program.workouts.size)
        assertTrue(program.weeks.last().isDeload)
    }

    @Test
    fun `persists week one day one exactly as prescribed`() = runTest {
        val template = programRepository.getCurrentWorkoutTemplate()!!

        assertEquals(1, template.weekNumber)
        assertEquals(1, template.dayNumber)

        val top = template.exercises.first()
        assertEquals("Przysiad", top.exercise.name)
        assertEquals(ExerciseType.TOP, top.type)
        assertEquals("1x3x187.5kg", top.scheme!!.format())

        val backOff = template.exercises[1]
        assertEquals("3x3x175kg", backOff.scheme!!.format())

        val benchVolume = template.exercises[2]
        assertEquals("Ławka", benchVolume.exercise.name)
        assertEquals("4x6x125kg", benchVolume.scheme!!.format())
    }

    @Test
    fun `persists accessory work without a prescribed load`() = runTest {
        val template = programRepository.getCurrentWorkoutTemplate()!!
        val accessory = template.exercises.first { it.type == ExerciseType.ACCESSORY }

        assertEquals("Skos Smith", accessory.exercise.name)
        assertEquals(3, accessory.plannedSets.size)
        assertNull(accessory.plannedSets.first().weight)
        assertEquals(12, accessory.plannedSets.first().reps)
        assertEquals("6-7", accessory.plannedSets.first().targetRpe!!.format())
    }

    @Test
    fun `the deload week drops accessory work`() = runTest {
        val program = programRepository.getActiveProgram()!!
        val deloadDay = program.weeks.last().workouts.first()

        assertEquals(2, deloadDay.exercises.size)
        assertTrue(deloadDay.exercises.all { it.type == ExerciseType.DELOAD })
    }

    @Test
    fun `stores the reference maxes the block was generated from`() = runTest {
        val maxes = referenceMaxRepository.observeReferenceMaxes().first()
            .associate { it.category to it.weight.format() }

        assertEquals("210", maxes[ExerciseCategory.SQUAT])
        assertEquals("225", maxes[ExerciseCategory.DEADLIFT])
        assertEquals("170", maxes[ExerciseCategory.BENCH_PRESS])
    }

    @Test
    fun `seeding twice does not duplicate the catalogue`() = runTest {
        DatabaseSeeder(database).seedIfNeeded()

        assertEquals(12, database.exerciseDao().count())
        assertEquals(1, database.trainingProgramDao().programCount())
    }

    @Test
    fun `starting a session snapshots the plan onto every set`() = runTest {
        val template = programRepository.getCurrentWorkoutTemplate()!!
        val id = sessionRepository.startSession(template, LocalDate.of(2026, 3, 4), clock.instant())

        val session = sessionRepository.getSession(id)!!
        assertEquals(WorkoutStatus.IN_PROGRESS, session.status)
        assertEquals(template.exercises.size, session.exercises.size)

        val topSet = session.exercises.first().sets.single()
        assertEquals("187.5", topSet.plannedWeight!!.format())
        assertEquals(3, topSet.plannedReps)
        assertEquals("8", topSet.plannedRpe!!.format())
        // Pre-filled with the plan so only deviations need typing.
        assertEquals("187.5", topSet.actualWeight!!.format())
        assertEquals(false, topSet.completed)
    }

    @Test
    fun `history keeps the planned values after the program is edited away`() = runTest {
        val template = programRepository.getCurrentWorkoutTemplate()!!
        val id = sessionRepository.startSession(template, LocalDate.of(2026, 3, 4), clock.instant())

        // Deleting the template must not take the logged session with it.
        database.compileStatement("DELETE FROM planned_set").executeUpdateDelete()
        database.compileStatement("DELETE FROM exercise_template").executeUpdateDelete()

        val session = sessionRepository.getSession(id)!!
        assertEquals("187.5", session.exercises.first().sets.single().plannedWeight!!.format())
    }

    @Test
    fun `a logged set survives being read back through the mapper`() = runTest {
        val template = programRepository.getCurrentWorkoutTemplate()!!
        val id = sessionRepository.startSession(template, LocalDate.of(2026, 3, 4), clock.instant())
        val setId = sessionRepository.getSession(id)!!.exercises.first().sets.single().id

        sessionRepository.updateSet(
            setId,
            SetResult(
                weight = Weight.of(192.5),
                reps = 3,
                rpe = Rpe.of(8.5),
                completed = true,
                notes = "felt heavy",
            ),
        )

        val set = sessionRepository.getSession(id)!!.exercises.first().sets.single()
        assertEquals("192.5", set.actualWeight!!.format())
        assertEquals(3, set.actualReps)
        assertEquals("8.5", set.actualRpe!!.format())
        assertTrue(set.completed)
        assertEquals("felt heavy", set.notes)
        assertEquals(577.5, set.volume.kilograms, 0.001)
    }

    @Test
    fun `an in-progress session is what the active session flow returns`() = runTest {
        val template = programRepository.getCurrentWorkoutTemplate()!!
        val id = sessionRepository.startSession(template, LocalDate.of(2026, 3, 4), clock.instant())

        assertEquals(id, sessionRepository.observeActiveSession().first()!!.id)

        sessionRepository.completeSession(id, clock.instant(), TrainingVolume.of(1000.0))

        assertNull(sessionRepository.observeActiveSession().first())
        assertEquals(1, sessionRepository.observeHistory().first().size)
    }

    @Test
    fun `removing a set deletes only that row`() = runTest {
        val template = programRepository.getCurrentWorkoutTemplate()!!
        val id = sessionRepository.startSession(template, LocalDate.of(2026, 3, 4), clock.instant())
        val backOff = sessionRepository.getSession(id)!!.exercises[1]

        sessionRepository.removeSet(backOff.sets.first().id)

        val after = sessionRepository.getSession(id)!!.exercises[1]
        assertEquals(backOff.sets.size - 1, after.sets.size)
    }

    @Test
    fun `an added exercise starts with one empty set`() = runTest {
        val template = programRepository.getCurrentWorkoutTemplate()!!
        val id = sessionRepository.startSession(template, LocalDate.of(2026, 3, 4), clock.instant())
        val exerciseId = database.exerciseDao().getByName("RDL")!!.id

        val logId = sessionRepository.addExercise(id, exerciseId, ExerciseType.ACCESSORY)

        val added = sessionRepository.getSession(id)!!.exercises.first { it.id == logId }
        assertEquals("RDL", added.exercise.name)
        assertEquals(1, added.sets.size)
        assertNull(added.sets.single().plannedWeight)
    }

    @Test
    fun `deleting a session cascades to its logs and sets`() = runTest {
        val template = programRepository.getCurrentWorkoutTemplate()!!
        val id = sessionRepository.startSession(template, LocalDate.of(2026, 3, 4), clock.instant())

        database.compileStatement("DELETE FROM workout_session WHERE id = $id").executeUpdateDelete()

        assertNull(sessionRepository.getSession(id))
        database.query("SELECT COUNT(*) FROM set_log", emptyArray()).use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun `updating a reference max keeps it observable`() = runTest {
        referenceMaxRepository.updateReferenceMax(
            ExerciseCategory.SQUAT,
            Weight.of(215.0),
            LocalDate.of(2026, 3, 4),
        )

        val squat = referenceMaxRepository.getReferenceMax(ExerciseCategory.SQUAT)!!
        assertEquals("215", squat.weight.format())
        assertEquals(LocalDate.of(2026, 3, 4), squat.updatedOn)
        assertEquals(3, referenceMaxRepository.observeReferenceMaxes().first().size)
    }

    @Test
    fun `program progress moves through the sequence`() = runTest {
        val first = programRepository.getCurrentWorkoutTemplate()!!
        val next = programRepository.getWorkoutTemplateAfter(first.sequenceNumber)!!

        assertEquals(1, next.weekNumber)
        assertEquals(2, next.dayNumber)

        programRepository.setCurrentWorkoutTemplate(next.id)
        assertNotNull(programRepository.getCurrentWorkoutTemplate())
        assertEquals(next.id, programRepository.getCurrentWorkoutTemplate()!!.id)

        programRepository.setCurrentWorkoutTemplate(null)
        assertNull(programRepository.getCurrentWorkoutTemplate())
    }
}
