package com.sobrietree.android.engine

import com.sobrietree.android.BeerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Shields, from the day boundary inward.
 *
 * The engine tests covered StreakEngine and DayBoundary separately, and both
 * passed while the app still got this wrong: a mis-read rollover hour meant the
 * day someone drank on never became a *completed* day, so the streak walk never
 * saw it and never spent a shield. The bug lived in the seam, so these tests
 * start where the app does - at a wall-clock time and a rollover setting.
 */
class ShieldOnLapseTest {

    private val start = LocalDate.of(2026, 8, 1)

    private fun drink(date: LocalDate, count: Int = 1) =
        (0 until count).map {
            BeerEntry("$date-$it", "Beer", 5.2, 500.0, date.toString(), "")
        }

    /** Builds the ledger the app would build at [now] with this rollover hour. */
    private fun ledgerAt(now: LocalDateTime, rolloverHour: Int, entries: List<BeerEntry>) =
        DayLedger(entries, start, DayBoundary.effectiveDate(now, rolloverHour))

    @Test
    fun `drinking yesterday spends a shield once the day has closed`() {
        // Dry 1-26 August (26 AF days, 3 shields), drank on the 27th.
        // It is 09:59 on the 28th with the rollover at 23.
        val entries = drink(LocalDate.of(2026, 8, 27), count = 3)
        val ledger = ledgerAt(LocalDateTime.of(2026, 8, 28, 9, 59), 23, entries)

        val r = StreakEngine.compute(ledger, weeklyGoalMl = 0.0, alreadyBridged = emptySet())

        assertEquals(LocalDate.of(2026, 8, 28), ledger.todayEffective)
        assertTrue("the lapse should be bridged", LocalDate.of(2026, 8, 27) in r.newlyBridgedDates)
        assertEquals(2, r.shieldsHeld)   // 26 AF days earns 3; one is now spent
    }

    @Test
    fun `the streak survives the bridged day`() {
        val entries = drink(LocalDate.of(2026, 8, 27))
        val ledger = ledgerAt(LocalDateTime.of(2026, 8, 28, 9, 59), 23, entries)
        val r = StreakEngine.compute(ledger, 0.0, emptySet())
        // 1-26 dry, 27th bridged: the run reaches back to tracking start. The
        // bridged day keeps the run alive but isn't itself an alcohol-free day,
        // so it doesn't add to the count - 26 dry days, not 27.
        assertEquals(26, r.currentStreak)
    }

    @Test
    fun `today's drinks do not spend a shield while the day is still open`() {
        // Deliberate: nothing counts until a day closes, so an evening that is
        // still in progress can't cost you a shield you might not need.
        val entries = drink(LocalDate.of(2026, 8, 28), count = 3)
        val ledger = ledgerAt(LocalDateTime.of(2026, 8, 28, 9, 59), 23, entries)
        val r = StreakEngine.compute(ledger, 0.0, emptySet())
        assertTrue(r.newlyBridgedDates.isEmpty())
        assertEquals(3, r.shieldsHeld)
    }

    @Test
    fun `the old reading of a late rollover hid the lapse entirely`() {
        // What the app used to do: "before hour 23, yesterday is still running",
        // so at 09:59 on the 28th it thought the day was the 27th - and the
        // 27th, being in progress, was never examined. No lapse, no shield.
        val entries = drink(LocalDate.of(2026, 8, 27), count = 3)
        val brokenToday = LocalDate.of(2026, 8, 27)
        val ledger = DayLedger(entries, start, brokenToday)

        val r = StreakEngine.compute(ledger, 0.0, emptySet())
        assertTrue("the drinking day was invisible", r.newlyBridgedDates.isEmpty())
        assertEquals(3, r.shieldsHeld)   // untouched, which is what was reported
    }

    @Test
    fun `a second consecutive lapse ends the streak rather than spending another`() {
        val entries = drink(LocalDate.of(2026, 8, 26)) + drink(LocalDate.of(2026, 8, 27))
        val ledger = ledgerAt(LocalDateTime.of(2026, 8, 28, 9, 59), 23, entries)
        val r = StreakEngine.compute(ledger, 0.0, emptySet())
        assertEquals(0, r.currentStreak)
        assertTrue(r.newlyBridgedDates.isEmpty())
    }

    @Test
    fun `a shield is never spent twice for the same day`() {
        val entries = drink(LocalDate.of(2026, 8, 27))
        val ledger = ledgerAt(LocalDateTime.of(2026, 8, 28, 9, 59), 23, entries)

        val first = StreakEngine.compute(ledger, 0.0, emptySet())
        val persisted = first.newlyBridgedDates.toSet()
        val second = StreakEngine.compute(ledger, 0.0, persisted)

        assertTrue(second.newlyBridgedDates.isEmpty())
        assertEquals(first.shieldsHeld, second.shieldsHeld)
    }

    @Test
    fun `going over the goal without drinking nothing is still a lapse`() {
        // Worth stating: a shield answers "did you drink", not "did you exceed
        // your goal". Three drinks against a goal of two is one lapse day, and
        // costs exactly one shield - not one per drink over.
        val entries = drink(LocalDate.of(2026, 8, 27), count = 3)
        val ledger = ledgerAt(LocalDateTime.of(2026, 8, 28, 9, 59), 23, entries)
        val r = StreakEngine.compute(ledger, 0.0, emptySet())
        assertEquals(1, r.newlyBridgedDates.size)
    }
}
