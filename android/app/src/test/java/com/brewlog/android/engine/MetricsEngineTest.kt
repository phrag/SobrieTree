package com.brewlog.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MetricsEngineTest {

    private val start = LocalDate.of(2026, 6, 1)
    private val today = LocalDate.of(2026, 7, 10)   // Friday, week starts Mon 6 Jul

    @Test
    fun `week and month totals`() {
        val entries = listOf(
            TestFixtures.entry(LocalDate.of(2026, 7, 6)),          // this week
            TestFixtures.entry(LocalDate.of(2026, 7, 10)),         // today
            TestFixtures.entry(LocalDate.of(2026, 7, 4)),          // last week, within 30d
            TestFixtures.entry(LocalDate.of(2026, 6, 5))           // outside 30d window
        )
        val ledger = DayLedger(entries, start, today)
        val m = MetricsEngine.compute(ledger, 1000.0, 7000.0, 1500.0)
        assertEquals(500.0, m.todayMl, 0.001)
        assertEquals(1000.0, m.weekMl, 0.001)
        assertEquals(1500.0, m.monthMl, 0.001)
    }

    @Test
    fun `goal falls back to baseline when unset`() {
        val ledger = DayLedger(emptyList(), start, today)
        val m = MetricsEngine.compute(ledger, 0.0, 0.0, 1500.0)
        assertEquals(1500.0, m.effectiveDailyGoalMl, 0.001)
        assertEquals(1500.0 * 7, m.effectiveWeeklyGoalMl, 0.001)
    }

    @Test
    fun `reduction percentages positive when below baseline`() {
        val entries = listOf(TestFixtures.entry(today))            // 500ml today
        val ledger = DayLedger(entries, start, today)
        val m = MetricsEngine.compute(ledger, 1000.0, 7000.0, 1000.0)
        assertEquals(50.0, m.reductionDailyPct, 0.001)
        assertTrue(m.reductionWeeklyPct > 0)
        assertFalse(m.overDailyGoal)
    }

    @Test
    fun `over goal flags`() {
        val entries = (0..2).map { TestFixtures.entry(today) }     // 1500ml today
        val ledger = DayLedger(entries, start, today)
        val m = MetricsEngine.compute(ledger, 1000.0, 7000.0, 1000.0)
        assertTrue(m.overDailyGoal)
        assertEquals(1.5, m.dailyRatio, 0.001)
    }
}
