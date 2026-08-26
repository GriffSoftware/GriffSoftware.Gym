package com.griffgym.domain

import com.griffgym.domain.model.OneRepMaxCalculator
import com.griffgym.domain.model.Weight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneRepMaxCalculatorTest {

    @Test
    fun `100 kg for five reps estimates about 116_67 kg`() {
        val estimate = OneRepMaxCalculator.estimate(Weight.of(100.0), 5)
        assertEquals(116.67, estimate!!.weight.kilograms, 0.01)
    }

    @Test
    fun `a single is already a true max and is returned untouched`() {
        val estimate = OneRepMaxCalculator.estimate(Weight.of(200.0), 1)
        assertEquals(Weight.of(200.0), estimate!!.weight)
    }

    @Test
    fun `keeps the set it was derived from`() {
        val estimate = OneRepMaxCalculator.estimate(Weight.of(192.5), 3)!!
        assertEquals(Weight.of(192.5), estimate.sourceWeight)
        assertEquals(3, estimate.sourceReps)
    }

    @Test
    fun `flags estimates past ten reps as unreliable`() {
        assertTrue(OneRepMaxCalculator.estimate(Weight.of(100.0), 10)!!.isReliable)
        assertFalse(OneRepMaxCalculator.estimate(Weight.of(100.0), 15)!!.isReliable)
    }

    @Test
    fun `has nothing to estimate from zero reps or zero load`() {
        assertNull(OneRepMaxCalculator.estimate(Weight.of(100.0), 0))
        assertNull(OneRepMaxCalculator.estimate(Weight.ZERO, 5))
    }
}
