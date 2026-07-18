package com.brewlog.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayLedgerTest {

    private val start = LocalDate.of(2026, 7, 1)
    private val today = LocalDate.of(2026, 7, 10)

    @Test
    fun `zero abv entries do not count toward totals`() {
        val ledger = DayLedger(
            entries = listOf(
                TestFixtures.entry(start, volumeMl = 500.0, abv = 0.0),
                TestFixtures.entry(start, volumeMl = 300.0, abv = 5.0)
            ),
            trackingStart = start,
            todayEffective = today
        )
        assertEquals(300.0, ledger.totalFor(start), 0.001)
    }

    @Test
    fun `af day requires completed date inside tracking window`() {
        val ledger = TestFixtures.ledger(start, today, drinkDays = listOf(start.plusDays(1)))
        assertTrue(ledger.isCompletedAfDay(start))
        assertFalse(ledger.isCompletedAfDay(start.plusDays(1)))     // drank
        assertFalse(ledger.isCompletedAfDay(today))                 // not completed yet
        assertFalse(ledger.isCompletedAfDay(start.minusDays(1)))    // before tracking
    }

    @Test
    fun `today is provisional`() {
        val clean = TestFixtures.ledger(start, today)
        assertTrue(clean.isTodayAfSoFar)
        val drank = TestFixtures.ledger(start, today, drinkDays = listOf(today))
        assertFalse(drank.isTodayAfSoFar)
    }

    @Test
    fun `completed days span tracking start until yesterday`() {
        val ledger = TestFixtures.ledger(start, today)
        assertEquals(9, ledger.completedDays.size)
        assertEquals(start, ledger.completedDays.first())
        assertEquals(today.minusDays(1), ledger.completedDays.last())
    }

    @Test
    fun `week start honours configured day`() {
        // 2026-07-10 is a Friday; Monday of that week is 2026-07-06
        val ledger = TestFixtures.ledger(start, today)
        assertEquals(LocalDate.of(2026, 7, 6), ledger.weekStartOf(today))
    }

    @Test
    fun `range total sums inclusive`() {
        val ledger = TestFixtures.ledger(
            start, today,
            drinkDays = listOf(start, start.plusDays(1), start.plusDays(2))
        )
        assertEquals(1500.0, ledger.totalForRange(start, start.plusDays(2)), 0.001)
        assertEquals(1000.0, ledger.totalForRange(start, start.plusDays(1)), 0.001)
    }
}
