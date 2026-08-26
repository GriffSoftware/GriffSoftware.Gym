package com.griffgym.application

import com.griffgym.application.stats.GetPersonalRecordsUseCase
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GetPersonalRecordsUseCaseTest {

    @Test
    fun `keeps the heaviest genuine single and the best estimate apart`() = runTest {
        val repository = FakeWorkoutSessionRepository(
            listOf(
                session(
                    id = 1,
                    date = LocalDate.of(2026, 1, 5),
                    exercises = listOf(
                        exerciseLog(id = 1, sets = listOf(setLog(1, weight = 200.0, reps = 1))),
                    ),
                ),
                session(
                    id = 2,
                    date = LocalDate.of(2026, 1, 12),
                    exercises = listOf(
                        exerciseLog(id = 2, sets = listOf(setLog(2, weight = 192.5, reps = 3))),
                    ),
                ),
            ),
        )

        val squatRecord = GetPersonalRecordsUseCase(repository)().first()
            .first { it.category == ExerciseCategory.SQUAT }

        assertEquals("200", squatRecord.bestActual!!.weight.format())
        assertEquals(false, squatRecord.bestActual!!.isEstimate)
        // 192.5 x 3 estimates to 211.75, which beats the 200 kg single.
        assertEquals(211.75, squatRecord.bestEstimated!!.weight.kilograms, 0.01)
        // The estimate remembers the bar it came from, not just the number it produced.
        assertEquals("192.5", squatRecord.bestEstimated!!.liftedWeight.format())
        assertEquals(3, squatRecord.bestEstimated!!.reps)
    }

    @Test
    fun `accessory work never becomes a record`() = runTest {
        val repository = FakeWorkoutSessionRepository(
            listOf(
                session(
                    id = 1,
                    exercises = listOf(
                        exerciseLog(
                            id = 1,
                            exercise = accessory,
                            type = ExerciseType.ACCESSORY,
                            sets = listOf(setLog(1, weight = 400.0, reps = 1)),
                        ),
                    ),
                ),
            ),
        )

        GetPersonalRecordsUseCase(repository)().first().forEach { record ->
            assertNull(record.bestActual)
            assertNull(record.bestEstimated)
        }
    }

    @Test
    fun `unticked sets do not count`() = runTest {
        val repository = FakeWorkoutSessionRepository(
            listOf(
                session(
                    id = 1,
                    exercises = listOf(
                        exerciseLog(
                            id = 1,
                            sets = listOf(setLog(1, weight = 300.0, reps = 1, completed = false)),
                        ),
                    ),
                ),
            ),
        )

        val squatRecord = GetPersonalRecordsUseCase(repository)().first()
            .first { it.category == ExerciseCategory.SQUAT }

        assertNull(squatRecord.bestActual)
        assertNull(squatRecord.bestEstimated)
    }

    @Test
    fun `a lifter with no history has no records`() = runTest {
        val records = GetPersonalRecordsUseCase(FakeWorkoutSessionRepository())().first()

        assertEquals(3, records.size)
        records.forEach { assertEquals(false, it.hasAnyRecord) }
    }
}
