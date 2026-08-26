package com.griffgym.application

import com.griffgym.application.workout.SaveSetResultUseCase
import com.griffgym.application.workout.SetField
import com.griffgym.application.workout.SetInput
import com.griffgym.application.workout.SetValidation
import com.griffgym.application.workout.UpdateSetResultUseCase
import com.griffgym.application.workout.ValidateSetInputUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetInputValidationTest {

    private val validate = ValidateSetInputUseCase()

    @Test
    fun `accepts a decimal load typed with either separator`() {
        listOf("117.5", "117,5").forEach { input ->
            val result = validate(SetInput(input, "5", "8"), requireComplete = true)
            assertTrue(result is SetValidation.Valid)
            assertEquals("117.5", (result as SetValidation.Valid).result.weight!!.format())
        }
    }

    @Test
    fun `rejects an RPE outside the scale`() {
        val result = validate(SetInput("100", "5", "11"), requireComplete = true)
        assertEquals(setOf(SetField.RPE), (result as SetValidation.Invalid).invalidFields)
    }

    @Test
    fun `accepts half step RPE values`() {
        val result = validate(SetInput("100", "5", "8.5"), requireComplete = true)
        assertEquals(8.5, (result as SetValidation.Valid).result.rpe!!.value, 0.001)
    }

    @Test
    fun `RPE stays optional even for a completed set`() {
        val result = validate(SetInput("100", "5", ""), requireComplete = true)
        assertNull((result as SetValidation.Valid).result.rpe)
    }

    @Test
    fun `a completed set needs both a load and reps`() {
        val result = validate(SetInput("", "", "8"), requireComplete = true)
        assertEquals(
            setOf(SetField.WEIGHT, SetField.REPS),
            (result as SetValidation.Invalid).invalidFields,
        )
    }

    @Test
    fun `a set still being edited may be blank`() {
        val result = validate(SetInput("", "", ""), requireComplete = false)
        assertTrue(result is SetValidation.Valid)
    }

    @Test
    fun `zero and negative reps are rejected`() {
        listOf("0", "-3").forEach { reps ->
            val result = validate(SetInput("100", reps, ""), requireComplete = true)
            assertTrue("reps='$reps' should be invalid", result is SetValidation.Invalid)
        }
    }

    @Test
    fun `saving a valid set writes it through and marks it done`() = runTest {
        val repository = FakeWorkoutSessionRepository()
        SaveSetResultUseCase(repository, validate)(7, SetInput("192.5", "3", "8"))

        val (id, result) = repository.updatedSets.single()
        assertEquals(7L, id)
        assertEquals(true, result.completed)
        assertEquals("192.5", result.weight!!.format())
    }

    @Test
    fun `an invalid set is never written to the database`() = runTest {
        val repository = FakeWorkoutSessionRepository()
        SaveSetResultUseCase(repository, validate)(7, SetInput("", "3", "8"))

        assertTrue(repository.updatedSets.isEmpty())
    }

    @Test
    fun `editing persists partial input without completing the set`() = runTest {
        val repository = FakeWorkoutSessionRepository()
        UpdateSetResultUseCase(repository, validate)(7, SetInput("19", "", ""), completed = false)

        assertEquals(false, repository.updatedSets.single().second.completed)
    }
}
