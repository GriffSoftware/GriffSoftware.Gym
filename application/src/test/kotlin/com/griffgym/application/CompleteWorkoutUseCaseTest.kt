package com.griffgym.application

import com.griffgym.application.metrics.CalculateWorkoutVolumeUseCase
import com.griffgym.application.workout.CompleteWorkoutUseCase
import com.griffgym.domain.model.WorkoutStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CompleteWorkoutUseCaseTest {

    private val clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val programRepository = FakeTrainingProgramRepository()

    private fun useCase(sessionRepository: FakeWorkoutSessionRepository) = CompleteWorkoutUseCase(
        sessionRepository = sessionRepository,
        programRepository = programRepository,
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
}
