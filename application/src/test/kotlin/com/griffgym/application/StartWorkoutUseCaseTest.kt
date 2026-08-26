package com.griffgym.application

import com.griffgym.application.workout.StartWorkoutUseCase
import com.griffgym.domain.model.WorkoutStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StartWorkoutUseCaseTest {

    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `snapshots the current template into a new session`() = runTest {
        val sessionRepository = FakeWorkoutSessionRepository()
        val programRepository = FakeTrainingProgramRepository()

        val result = StartWorkoutUseCase(sessionRepository, programRepository, clock)()

        assertTrue(result.isSuccess)
        assertEquals(1, sessionRepository.startedFrom!!.weekNumber)
        assertEquals(1, sessionRepository.startedFrom!!.dayNumber)
    }

    @Test
    fun `starting twice resumes the running session instead of creating another`() = runTest {
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 42, status = WorkoutStatus.IN_PROGRESS)),
        )

        val result = StartWorkoutUseCase(sessionRepository, FakeTrainingProgramRepository(), clock)()

        assertEquals(42L, result.getOrNull())
        assertNull(sessionRepository.startedFrom)
    }

    @Test
    fun `there is nothing to start once the program is finished`() = runTest {
        val programRepository = FakeTrainingProgramRepository()
        programRepository.setCurrentWorkoutTemplate(null)

        val result = StartWorkoutUseCase(FakeWorkoutSessionRepository(), programRepository, clock)()

        assertTrue(result.isFailure)
    }
}
