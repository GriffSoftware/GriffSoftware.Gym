package com.griffgym.application

import com.griffgym.application.metrics.CalculateWorkoutVolumeUseCase
import com.griffgym.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateWorkoutVolumeUseCaseTest {

    private val calculate = CalculateWorkoutVolumeUseCase()

    @Test
    fun `sums weight times reps over every completed set`() {
        val workout = session(
            id = 1,
            exercises = listOf(
                exerciseLog(
                    id = 1,
                    sets = listOf(
                        setLog(1, weight = 187.5, reps = 3),
                        setLog(2, position = 2, weight = 175.0, reps = 3),
                    ),
                ),
            ),
        )

        assertEquals(1087.5, calculate(workout).kilograms, 0.001)
    }

    @Test
    fun `ignores sets that were never ticked off`() {
        val workout = session(
            id = 1,
            exercises = listOf(
                exerciseLog(
                    id = 1,
                    sets = listOf(
                        setLog(1, weight = 100.0, reps = 5, completed = true),
                        setLog(2, position = 2, weight = 100.0, reps = 5, completed = false),
                    ),
                ),
            ),
        )

        assertEquals(500.0, calculate(workout).kilograms, 0.001)
    }

    @Test
    fun `handles decimal loads without drift`() {
        val workout = session(
            id = 1,
            exercises = listOf(
                exerciseLog(id = 1, sets = listOf(setLog(1, weight = 132.5, reps = 6))),
            ),
        )

        assertEquals(795.0, calculate(workout).kilograms, 0.001)
    }

    @Test
    fun `accessory work with no load contributes nothing`() {
        val workout = session(
            id = 1,
            exercises = listOf(
                exerciseLog(
                    id = 1,
                    exercise = accessory,
                    type = ExerciseType.ACCESSORY,
                    sets = listOf(setLog(1, weight = null, reps = 12)),
                ),
            ),
        )

        assertEquals(0.0, calculate(workout).kilograms, 0.001)
    }
}
