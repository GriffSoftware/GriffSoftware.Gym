package com.griffgym.domain

import com.griffgym.domain.model.Weight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightTest {

    @Test
    fun `parses the half kilograms the program is built on`() {
        listOf("117.5", "132.5", "142.5", "162.5", "192.5").forEach { input ->
            assertEquals(input, Weight.parse(input)?.format())
        }
    }

    @Test
    fun `accepts a comma as the decimal separator`() {
        assertEquals(Weight.of(132.5), Weight.parse("132,5"))
    }

    @Test
    fun `formats whole kilograms without a trailing zero`() {
        assertEquals("150", Weight.of(150.0).format())
        assertEquals("192.5", Weight.of(192.5).format())
    }

    @Test
    fun `rejects input that is not a weight`() {
        assertNull(Weight.parse(""))
        assertNull(Weight.parse("abc"))
        assertNull(Weight.parse("-20"))
        assertNull(Weight.parse("1.2.3"))
    }

    @Test
    fun `arithmetic does not accumulate floating point noise`() {
        val total = (1..3).fold(Weight.ZERO) { acc, _ -> acc + Weight.of(0.1) }
        assertEquals("0.3", total.format())
    }

    @Test
    fun `percentage of a max is rounded to two decimals`() {
        assertEquals("110.2", Weight.of(116.0).percentage(95).format())
    }
}
