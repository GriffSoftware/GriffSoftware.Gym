package com.griffgym.domain

import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WeightRoundingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WeightRoundingPolicyTest {

    @Test
    fun `snaps a calculated load onto the nearest plate increment`() {
        assertEquals(160.0, WeightRoundingPolicy.round(159.2).kilograms, EPSILON)
        assertEquals(160.0, WeightRoundingPolicy.round(161.1).kilograms, EPSILON)
        assertEquals(162.5, WeightRoundingPolicy.round(161.4).kilograms, EPSILON)
    }

    @Test
    fun `leaves a load that is already loadable alone`() {
        listOf(100.0, 117.5, 132.5, 142.5, 162.5).forEach { kilograms ->
            assertEquals(kilograms, WeightRoundingPolicy.round(kilograms).kilograms, EPSILON)
        }
    }

    @Test
    fun `rounds a tie up rather than down`() {
        // 161.25 sits exactly between 160 and 162.5: the prescription wins over comfort.
        assertEquals(162.5, WeightRoundingPolicy.round(161.25).kilograms, EPSILON)
        assertEquals(2.5, WeightRoundingPolicy.round(1.25).kilograms, EPSILON)
    }

    @Test
    fun `rounds down below the halfway point`() {
        assertEquals(160.0, WeightRoundingPolicy.round(161.24).kilograms, EPSILON)
    }

    @Test
    fun `an empty bar stays an empty bar`() {
        assertEquals(Weight.ZERO, WeightRoundingPolicy.round(0.0))
        assertEquals(Weight.ZERO, WeightRoundingPolicy.round(1.0))
    }

    @Test
    fun `accepts a weight as well as a raw number`() {
        assertEquals(Weight.of(162.5), WeightRoundingPolicy.round(Weight.of(161.4)))
    }

    @Test
    fun `refuses a load that cannot be loaded at all`() {
        assertThrows(IllegalArgumentException::class.java) {
            WeightRoundingPolicy.round(-1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WeightRoundingPolicy.round(Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WeightRoundingPolicy.round(Double.POSITIVE_INFINITY)
        }
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
