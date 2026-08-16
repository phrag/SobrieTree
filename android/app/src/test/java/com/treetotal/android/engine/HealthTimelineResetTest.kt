package com.treetotal.android.engine

import com.treetotal.android.BeerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The recovery arc is keyed to the current unbroken run, not to lifetime
 * alcohol-free days: the body's clock restarts when drinking does.
 */
class HealthTimelineResetTest {

    private val start = LocalDate.of(2026, 7, 1)

    private fun drink(date: LocalDate) =
        BeerEntry(date.toString(), "Drink", 5.0, 500.0, date.toString(), "")

    @Test
    fun `an unbroken run reaches milestones on the expected days`() {
        // 1-10 July all dry, today is the 11th.
        val ledger = DayLedger(emptyList(), start, start.plusDays(10))
        assertEquals(start, HealthTimeline.dateReachedInStreak(ledger, 1))
        assertEquals(start.plusDays(2), HealthTimeline.dateReachedInStreak(ledger, 3))
        assertEquals(start.plusDays(6), HealthTimeline.dateReachedInStreak(ledger, 7))
        assertNull(HealthTimeline.dateReachedInStreak(ledger, 14))
    }

    @Test
    fun `drinking restarts the clock`() {
        // Dry 1-7 July, drank on the 8th, dry again 9-11. Today is the 12th.
        val ledger = DayLedger(listOf(drink(start.plusDays(7))), start, start.plusDays(11))
        // The run is only the 9th, 10th and 11th - three days.
        assertEquals(start.plusDays(8), HealthTimeline.dateReachedInStreak(ledger, 1))
        assertEquals(start.plusDays(10), HealthTimeline.dateReachedInStreak(ledger, 3))
        // A week's worth of cumulative dry days exists, but not in this run.
        assertNull(HealthTimeline.dateReachedInStreak(ledger, 7))
        // The cumulative view still counts them - it hit 7 dry days back on the
        // 7th, before the lapse - which is exactly why the two are separate.
        assertEquals(start.plusDays(6), HealthTimeline.dateReached(ledger, 7))
    }

    @Test
    fun `drinking today does not retroactively erase yesterday's run`() {
        // Today is in progress and never counts; the completed run is intact.
        val today = start.plusDays(5)
        val ledger = DayLedger(listOf(drink(today)), start, today)
        assertEquals(start.plusDays(4), HealthTimeline.dateReachedInStreak(ledger, 5))
    }

    @Test
    fun `a shielded lapse does not restart the clock`() {
        val lapse = start.plusDays(3)
        val ledger = DayLedger(listOf(drink(lapse)), start, start.plusDays(7))
        // Unshielded, the run is only the days after the lapse.
        assertNull(HealthTimeline.dateReachedInStreak(ledger, 7))
        // Shielded, the run spans the lapse and reaches day 7.
        assertEquals(
            start.plusDays(6),
            HealthTimeline.dateReachedInStreak(ledger, 7, bridgedDates = setOf(lapse))
        )
    }

    @Test
    fun `drinking on the very first tracked day leaves no run`() {
        val ledger = DayLedger(listOf(drink(start)), start, start.plusDays(1))
        assertNull(HealthTimeline.dateReachedInStreak(ledger, 1))
    }

    @Test
    fun `no completed days yet means no milestone`() {
        val ledger = DayLedger(emptyList(), start, start)
        assertNull(HealthTimeline.dateReachedInStreak(ledger, 1))
    }

    @Test
    fun `current stage follows the streak, so it can go backwards`() {
        // Reaching a week then drinking drops the stage back to nothing.
        assertEquals("h_7", HealthTimeline.current(7)?.id)
        assertNull(HealthTimeline.current(0))
    }
}
