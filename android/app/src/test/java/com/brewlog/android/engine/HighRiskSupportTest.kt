package com.brewlog.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HighRiskSupportTest {

    @Test
    fun `intensity tapers with days since start`() {
        assertEquals(HighRiskSupport.Intensity.INTENSIVE, HighRiskSupport.intensity(0))
        assertEquals(HighRiskSupport.Intensity.INTENSIVE, HighRiskSupport.intensity(7))
        assertEquals(HighRiskSupport.Intensity.STEADY, HighRiskSupport.intensity(8))
        assertEquals(HighRiskSupport.Intensity.STEADY, HighRiskSupport.intensity(21))
        assertEquals(HighRiskSupport.Intensity.LIGHT, HighRiskSupport.intensity(22))
        assertEquals(HighRiskSupport.Intensity.LIGHT, HighRiskSupport.intensity(365))
    }

    @Test
    fun `window covers before and after the start time`() {
        val start = 18 * 60 // 18:00
        assertTrue(HighRiskSupport.isInWindow(17 * 60 + 45, start))  // 30 min before -> in
        assertTrue(HighRiskSupport.isInWindow(18 * 60, start))       // exactly at
        assertTrue(HighRiskSupport.isInWindow(19 * 60 + 59, start))  // within 2h after
        assertFalse(HighRiskSupport.isInWindow(20 * 60 + 30, start)) // past window
        assertFalse(HighRiskSupport.isInWindow(12 * 60, start))      // midday
    }

    @Test
    fun `window wraps around midnight`() {
        val start = 23 * 60 + 45 // 23:45
        assertTrue(HighRiskSupport.isInWindow(23 * 60 + 30, start))  // 15 before
        assertTrue(HighRiskSupport.isInWindow(0 * 60 + 30, start))   // after midnight, within 2h
        assertFalse(HighRiskSupport.isInWindow(22 * 60, start))      // well before
    }

    @Test
    fun `every intensity has a pool and rotation is deterministic`() {
        val d = LocalDate.of(2026, 7, 19)
        for (i in HighRiskSupport.Intensity.values()) {
            val pool = HighRiskSupport.pools.getValue(i)
            assertTrue(pool.size >= 5)
            assertEquals(HighRiskSupport.message(i, d), HighRiskSupport.message(i, d))
        }
    }
}
