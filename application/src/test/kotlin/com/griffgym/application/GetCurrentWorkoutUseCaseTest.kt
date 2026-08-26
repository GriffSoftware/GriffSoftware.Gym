package com.griffgym.application

import com.griffgym.application.metrics.CalculateWorkoutVolumeUseCase
import com.griffgym.application.workout.CompleteWorkoutUseCase
import com.griffgym.application.workout.GetCurrentWorkoutUseCase
import com.griffgym.domain.model.CurrentWorkout
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.GeneratedProgram
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What Home and the log tab derive "what should the lifter be doing right now" from.
 *
 * The case that matters most here is not a normal one: an installation upgraded from before
 * cycles existed can hold a cycle row whose `status` still reads ACTIVE even though its
 * program has genuinely run out (see [com.griffgym.infrastructure.database.migration.MIGRATION_1_2]).
 * "No next workout, but a cycle to review" has to win regardless of that stored flag, or that
 * lifter is stranded on a dead end forever.
 */
class GetCurrentWorkoutUseCaseTest {

    private val clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val programRepository = FakeTrainingProgramRepository()
    private val referenceMaxRepository = FakeReferenceMaxRepository()
    private val cycleRepository = FakeTrainingCycleRepository(programRepository, referenceMaxRepository)

    private suspend fun startCycle() = cycleRepository.startCycle(
        program = GeneratedProgram(name = "Blok IV", weeks = emptyList()),
        referenceMaxes = ReferenceMaxSnapshot(
            squat = Weight.of(210.0),
            benchPress = Weight.of(170.0),
            deadlift = Weight.of(225.0),
        ),
        date = LocalDate.now(clock),
        startedAt = clock.instant(),
    )

    private fun useCase(sessionRepository: FakeWorkoutSessionRepository) = GetCurrentWorkoutUseCase(
        sessionRepository = sessionRepository,
        programRepository = programRepository,
        cycleRepository = cycleRepository,
    )

    @Test
    fun `a running session wins over everything else`() = runTest {
        startCycle()
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 1, templateId = 1, status = WorkoutStatus.IN_PROGRESS)),
        )

        val result = useCase(sessionRepository)().first()

        assertTrue(result is CurrentWorkout.Active)
    }

    @Test
    fun `with no session and a unit left, the next unit is planned`() = runTest {
        startCycle()
        val sessionRepository = FakeWorkoutSessionRepository()

        val result = useCase(sessionRepository)().first()

        assertTrue(result is CurrentWorkout.Planned)
    }

    @Test
    fun `nothing on disk at all means no program`() = runTest {
        val emptyPrograms = FakeTrainingProgramRepository.empty()
        val emptyCycles = FakeTrainingCycleRepository(emptyPrograms, referenceMaxRepository)
        val useCase = GetCurrentWorkoutUseCase(
            sessionRepository = FakeWorkoutSessionRepository(),
            programRepository = emptyPrograms,
            cycleRepository = emptyCycles,
        )

        val result = useCase().first()

        assertEquals(CurrentWorkout.NoProgram, result)
    }

    @Test
    fun `an upgraded installation whose program had already run out is a cycle to review`() = runTest {
        // Nothing in the pre-cycle schema could mark a finished program as anything other
        // than "active": the migration synthesises cycle one as ACTIVE, from whatever was on
        // disk, even when the lifter had genuinely already finished it. `programRepository`
        // mirrors that exactly: a pointer that is already null, under a cycle whose stored
        // status still says ACTIVE.
        startCycle()
        programRepository.setCurrentWorkoutTemplate(null)
        val sessionRepository = FakeWorkoutSessionRepository()

        val result = useCase(sessionRepository)().first()

        assertTrue("expected CycleCompleted, got $result", result is CurrentWorkout.CycleCompleted)
        val cycle = (result as CurrentWorkout.CycleCompleted).cycle
        assertEquals(1, cycle.cycleNumber)
        // The stored flag is exactly what the migration leaves behind: still ACTIVE. The use
        // case has to look past it, not trust it.
        assertEquals(CycleStatus.ACTIVE, cycle.status)
    }

    @Test
    fun `a cycle finished the normal way is also a cycle to review`() = runTest {
        startCycle()
        programRepository.setCurrentWorkoutTemplate(18)
        val sessionRepository = FakeWorkoutSessionRepository(
            listOf(session(id = 1, templateId = 18, status = WorkoutStatus.IN_PROGRESS)),
        )
        CompleteWorkoutUseCase(
            sessionRepository = sessionRepository,
            programRepository = programRepository,
            cycleRepository = cycleRepository,
            calculateVolume = CalculateWorkoutVolumeUseCase(),
            clock = clock,
        )(1)

        val result = useCase(FakeWorkoutSessionRepository())().first()

        assertTrue(result is CurrentWorkout.CycleCompleted)
        assertEquals(CycleStatus.COMPLETED, cycleRepository.getCurrentCycle()!!.status)
    }
}
