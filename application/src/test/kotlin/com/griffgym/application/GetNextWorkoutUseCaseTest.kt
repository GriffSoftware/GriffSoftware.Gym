package com.griffgym.application

import com.griffgym.application.workout.GetNextWorkoutUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetNextWorkoutUseCaseTest {

    private val programRepository = FakeTrainingProgramRepository()
    private val getNextWorkout = GetNextWorkoutUseCase(programRepository)

    @Test
    fun `day one is followed by day two of the same week`() = runTest {
        val next = getNextWorkout()!!
        assertEquals(1, next.weekNumber)
        assertEquals(2, next.dayNumber)
    }

    @Test
    fun `week one day three is followed by week two day one`() = runTest {
        val weekOneDayThree = programRepository.getWorkoutTemplate(3)!!
        val next = getNextWorkout.after(weekOneDayThree)!!

        assertEquals(2, next.weekNumber)
        assertEquals(1, next.dayNumber)
    }

    @Test
    fun `the last unit of the program has no successor`() = runTest {
        val lastUnit = programRepository.getWorkoutTemplate(18)!!
        assertEquals(6, lastUnit.weekNumber)
        assertEquals(3, lastUnit.dayNumber)
        assertNull(getNextWorkout.after(lastUnit))
    }
}
