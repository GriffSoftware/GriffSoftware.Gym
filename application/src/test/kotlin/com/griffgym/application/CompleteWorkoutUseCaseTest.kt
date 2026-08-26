package com.griffgym.application

import com.griffgym.application.metrics.CalculateWorkoutVolumeUseCase
import com.griffgym.application.workout.CompleteWorkoutUseCase
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.GeneratedProgram
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CompleteWorkoutUseCaseTest {

    private val clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val programRepository = FakeTrainingProgramRepository()
    private val referenceMaxRepository = FakeReferenceMaxRepository()
    private val cycleRepository =
        FakeTrainingCycleRepository(programRepository, referenceMaxRepository)

    /** Puts a cycle in place around the plan the fake program repository already holds. */
    private suspend fun startCycle() {
        cycleRepository.startCycle(
            program = GeneratedProgram(name = "Blok IV", weeks = emptyList()),
            referenceMaxes = ReferenceMaxSnapshot(
                squat = Weight.of(210.0),
                benchPress = Weight.of(170.0),
                deadlift = Weight.of(225.0),
            ),
            date = LocalDate.now(clock),
            startedAt = clock.instant(),
        )
    }

    private fun useCase(sessionRepository: FakeWorkoutSessionRepository) = CompleteWorkoutUseCase(
        sessionRepository = sessionRepository,
        programRepository = programRepository,
        cycleRepository = cycleRepository,
        calculateVolume = CalculateWorkoutVolumeUseCase(),
        clock = clock,
    )

    @Test
    fun `freezes the session volume on completion`() = runTest {
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(
                session(
                    id = 1,
                    templateId = 1,
                    status = WorkoutStatus.IN_PROGRESS,
                    exercises = listOf(
                        exerciseLog(id = 1, sets = listOf(setLog(1, weight = 187.5, reps = 3))),
                    ),
                ),
            ),
        )

        val result = useCase(sessionRepository)(1)

        assertTrue(result.isSuccess)
        assertEquals(562.5, sessionRepository.completedWith!!.second.kilograms, 0.001)
    }

    @Test
    fun `advances the program to the next unit`() = runTest {
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 1, templateId = 1, status = WorkoutStatus.IN_PROGRESS)),
        )

        useCase(sessionRepository)(1)

        val current = programRepository.getCurrentWorkoutTemplate()!!
        assertEquals(1, current.weekNumber)
        assertEquals(2, current.dayNumber)
    }

    @Test
    fun `finishing week one day three moves the plan to week two day one`() = runTest {
        programRepository.setCurrentWorkoutTemplate(3)
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 1, templateId = 3, status = WorkoutStatus.IN_PROGRESS)),
        )

        useCase(sessionRepository)(1)

        val current = programRepository.getCurrentWorkoutTemplate()!!
        assertEquals(2, current.weekNumber)
        assertEquals(1, current.dayNumber)
    }

    @Test
    fun `replaying an older session does not skip the plan forward`() = runTest {
        programRepository.setCurrentWorkoutTemplate(5)
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 9, templateId = 1, status = WorkoutStatus.IN_PROGRESS)),
        )

        useCase(sessionRepository)(9)

        assertEquals(5L, programRepository.getCurrentWorkoutTemplate()!!.id)
    }

    @Test
    fun `an already finished session cannot be completed twice`() = runTest {
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 1, templateId = 1, status = WorkoutStatus.COMPLETED)),
        )

        assertTrue(useCase(sessionRepository)(1).isFailure)
    }

    @Test
    fun `completing the last unit leaves the program with nothing current`() = runTest {
        programRepository.setCurrentWorkoutTemplate(18)
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 1, templateId = 18, status = WorkoutStatus.IN_PROGRESS)),
        )

        useCase(sessionRepository)(1)

        assertEquals(null, programRepository.getCurrentWorkoutTemplate())
    }

    @Test
    fun `week six day two still leaves the cycle active`() = runTest {
        startCycle()
        // Template 17 is week 6, day II — one deload session short of the end.
        programRepository.setCurrentWorkoutTemplate(17)
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 1, templateId = 17, status = WorkoutStatus.IN_PROGRESS)),
        )

        useCase(sessionRepository)(1)

        val cycle = cycleRepository.getCurrentCycle()!!
        assertEquals(CycleStatus.ACTIVE, cycle.status)
        assertNull(cycle.completedAt)
        assertEquals(18L, programRepository.getCurrentWorkoutTemplate()!!.id)
    }

    @Test
    fun `week six day three completes the cycle and stamps when it happened`() = runTest {
        startCycle()
        programRepository.setCurrentWorkoutTemplate(18)
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 1, templateId = 18, status = WorkoutStatus.IN_PROGRESS)),
        )

        useCase(sessionRepository)(1)

        val cycle = cycleRepository.getCurrentCycle()!!
        assertEquals(CycleStatus.COMPLETED, cycle.status)
        // Never the calendar: the moment the last scheduled unit was actually logged.
        assertEquals(clock.instant(), cycle.completedAt)
        assertNull(programRepository.getCurrentWorkoutTemplate())
    }

    @Test
    fun `finishing a cycle never creates the next one`() = runTest {
        startCycle()
        programRepository.setCurrentWorkoutTemplate(18)
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 1, templateId = 18, status = WorkoutStatus.IN_PROGRESS)),
        )

        useCase(sessionRepository)(1)

        // The app waits, visibly, for the lifter to decide. One cycle, one plan, no new block.
        assertEquals(1, cycleRepository.startedPrograms.size)
        assertEquals(1, cycleRepository.getCurrentCycle()!!.cycleNumber)
    }

    @Test
    fun `replaying an old session at the end of a cycle does not reopen or reclose it`() = runTest {
        startCycle()
        programRepository.setCurrentWorkoutTemplate(18)
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 9, templateId = 4, status = WorkoutStatus.IN_PROGRESS)),
        )

        useCase(sessionRepository)(9)

        assertEquals(CycleStatus.ACTIVE, cycleRepository.getCurrentCycle()!!.status)
        assertEquals(18L, programRepository.getCurrentWorkoutTemplate()!!.id)
    }
}
