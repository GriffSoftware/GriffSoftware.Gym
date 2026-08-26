package com.griffgym.domain

import com.griffgym.domain.model.Rpe
import com.griffgym.domain.model.RpeTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RpeTest {

    @Test
    fun `accepts the whole one to ten range`() {
        assertNotNull(Rpe.ofOrNull(1.0))
        assertNotNull(Rpe.ofOrNull(10.0))
        assertNotNull(Rpe.ofOrNull(7.5))
    }

    @Test
    fun `rejects values outside the scale`() {
        assertNull(Rpe.ofOrNull(0.5))
        assertNull(Rpe.ofOrNull(10.5))
        assertNull(Rpe.ofOrNull(-1.0))
        assertNull(Rpe.parse("abc"))
        assertNull(Rpe.parse(""))
    }

    @Test
    fun `snaps to the nearest half step`() {
        assertEquals(7.5, Rpe.of(7.4).value, 0.001)
        assertEquals(8.0, Rpe.of(7.9).value, 0.001)
    }

    @Test
    fun `parses a comma as the decimal separator`() {
        assertEquals(8.5, Rpe.parse("8,5")?.value)
    }

    @Test
    fun `formats whole values without a decimal`() {
        assertEquals("8", Rpe.of(8.0).format())
        assertEquals("8.5", Rpe.of(8.5).format())
    }

    @Test
    fun `a target renders as a value or a range`() {
        assertEquals("8", RpeTarget.exact(8.0).format())
        assertEquals("6-7", RpeTarget.range(6.0, 7.0).format())
        assertTrue(RpeTarget.range(6.0, 7.0).isRange)
    }
}
