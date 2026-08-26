package com.griffgym.application

import com.griffgym.application.metrics.CalculateEstimated1RmUseCase
import com.griffgym.domain.model.Weight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculateEstimated1RmUseCaseTest {

    private val calculate = CalculateEstimated1RmUseCase()

    @Test
    fun `100 kg for five reps is about 116_67 kg`() {
        assertEquals(116.67, calculate(Weight.of(100.0), 5)!!.weight.kilograms, 0.01)
    }

    @Test
    fun `a single returns the weight that was lifted`() {
        assertEquals(Weight.of(215.0), calculate(Weight.of(215.0), 1)!!.weight)
    }

    @Test
    fun `accepts decimal input typed with a comma`() {
        assertEquals(Weight.of(192.5), calculate("192,5", 1)!!.weight)
    }

    @Test
    fun `rejects input that is not a weight`() {
        assertNull(calculate("", 5))
        assertNull(calculate("heavy", 5))
    }
}
