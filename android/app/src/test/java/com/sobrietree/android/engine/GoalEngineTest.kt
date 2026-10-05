package com.sobrietree.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalEngineTest {

    private val start = LocalDate.of(2026, 6, 1)
    private val today = LocalDate.of(2026, 7, 10)   // Friday, week starts Mon 6 Jul

    /** Goal 1 drink (500ml) a day, 4 drinks (2000ml) a week - deliberately not ×7. */
    private fun metrics(drinkDays: List<LocalDate>) = MetricsEngine.compute(
        TestFixtures.ledger(start, today, drinkDays),
        goalDailyMl = 500.0,
        goalWeeklyMl = 2000.0,
        baselineDailyMl = 1000.0
    )

    @Test
    fun `daily mode counts down the day, weekly mode counts down the week`() {
        // Mon, Wed and today: 1500ml this week, 500ml of it today.
        val m = metrics(listOf(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8), today))

        val daily = GoalEngine.ring(GoalMode.DAILY, m)
        assertEquals(500.0, daily.allowanceMl, 0.001)
        assertEquals(500.0, daily.consumedMl, 0.001)
        assertEquals(1.0, daily.consumedRatio, 0.001)

        val weekly = GoalEngine.ring(GoalMode.WEEKLY, m)
        assertEquals(2000.0, weekly.allowanceMl, 0.001)
        assertEquals(1500.0, weekly.consumedMl, 0.001)
        assertEquals(0.75, weekly.consumedRatio, 0.001)
        assertEquals(500.0, weekly.remainingMl, 0.001)
    }

    @Test
    fun `a weekly ring does not refill overnight`() {
        // The point of a weekly allowance: Monday's drinking is still spent on
        // Friday. The daily ring, by contrast, is full again by then.
        val m = metrics(listOf(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 7)))

        val daily = GoalEngine.ring(GoalMode.DAILY, m)
        assertTrue("nothing logged today, so the daily ring is whole", daily.untouched)
        assertEquals(0.0, daily.consumedRatio, 0.001)

        val weekly = GoalEngine.ring(GoalMode.WEEKLY, m)
        assertFalse("the week still carries Monday and Tuesday", weekly.untouched)
        assertEquals(0.5, weekly.consumedRatio, 0.001)
    }

    @Test
    fun `an overspent allowance reports nothing left, never a negative`() {
        // Five 500ml days against a 2000ml week: 500ml over.
        val m = metrics((6..10).map { LocalDate.of(2026, 7, it) })
        val weekly = GoalEngine.ring(GoalMode.WEEKLY, m)

        assertTrue(weekly.overGoal)
        assertEquals(0.0, weekly.remainingMl, 0.001)
        // The ratio itself stays uncapped, so the UI can tell "just over" from "double".
        assertEquals(1.25, weekly.consumedRatio, 0.001)
    }

    @Test
    fun `days left in the window include today`() {
        val m = metrics(emptyList())
        // Friday: Fri, Sat and Sun are still to come.
        assertEquals(3, GoalEngine.ring(GoalMode.WEEKLY, m).daysLeftInWindow)
        // A day's allowance only ever has the day itself left.
        assertEquals(1, GoalEngine.ring(GoalMode.DAILY, m).daysLeftInWindow)
    }

    @Test
    fun `with no goal or baseline the ring stays empty rather than dividing by zero`() {
        val m = MetricsEngine.compute(
            TestFixtures.ledger(start, today, listOf(today)),
            goalDailyMl = 0.0,
            goalWeeklyMl = 0.0,
            baselineDailyMl = 0.0
        )
        listOf(GoalMode.DAILY, GoalMode.WEEKLY).forEach { mode ->
            val ring = GoalEngine.ring(mode, m)
            assertEquals(0.0, ring.consumedRatio, 0.001)
            assertFalse("no allowance means there is nothing to be over", ring.overGoal)
        }
    }

    @Test
    fun `weekly mode judges a single day by an even share of the week, not the stored daily goal`() {
        // The daily figure here (500ml) is a leftover from daily mode; someone
        // on a 2000ml week is pacing at ~286ml a day, and the week dots should
        // say so rather than flattering them against a goal they dropped.
        val m = metrics(emptyList())
        assertEquals(500.0, GoalEngine.dailyYardstickMl(GoalMode.DAILY, m), 0.001)
        assertEquals(2000.0 / 7, GoalEngine.dailyYardstickMl(GoalMode.WEEKLY, m), 0.001)
    }

    @Test
    fun `each goal prefills the other through the same seven-day conversion`() {
        assertEquals(3500.0, GoalEngine.weeklyFromDaily(500.0), 0.001)
        assertEquals(500.0, GoalEngine.dailyFromWeekly(3500.0), 0.001)
    }

    @Test
    fun `an unknown stored mode reads as daily`() {
        // Existing installs have no goal_mode key at all, and must keep the
        // daily behaviour they were set up with.
        assertEquals(GoalMode.DAILY, GoalMode.fromKey(null))
        assertEquals(GoalMode.DAILY, GoalMode.fromKey(""))
        assertEquals(GoalMode.DAILY, GoalMode.fromKey("fortnightly"))
        assertEquals(GoalMode.WEEKLY, GoalMode.fromKey("weekly"))
        // Round-trips through the key that gets persisted.
        GoalMode.entries.forEach { assertEquals(it, GoalMode.fromKey(it.key)) }
    }
}
