package com.treetotal.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakEngineTest {

    private val start = LocalDate.of(2026, 6, 1)

    private fun compute(
        today: LocalDate,
        drinkDays: List<LocalDate>,
        weeklyGoal: Double = 3500.0,
        alreadyBridged: Set<LocalDate> = emptySet()
    ): StreakEngine.Result =
        StreakEngine.compute(TestFixtures.ledger(start, today, drinkDays), weeklyGoal, alreadyBridged)

    @Test
    fun `all clean days count as total and streak`() {
        val today = start.plusDays(10)
        val r = compute(today, emptyList())
        assertEquals(10, r.totalAfDays)
        assertEquals(10, r.currentStreak)
        assertEquals(11, r.displayStreak) // today clean so far
        assertEquals(10, r.bestStreak)
    }

    @Test
    fun `drinking today drops the provisional day only`() {
        val today = start.plusDays(10)
        val r = compute(today, listOf(today))
        assertEquals(10, r.currentStreak)
        assertEquals(10, r.displayStreak)
    }

    @Test
    fun `two consecutive lapse days end the streak but not the total`() {
        val today = start.plusDays(20)
        val lapse = listOf(today.minusDays(6), today.minusDays(5))
        val r = compute(today, lapse)
        assertEquals(18, r.totalAfDays)      // cumulative never resets
        assertEquals(4, r.currentStreak)     // only days after the lapse pair
        assertTrue(r.bestStreak >= 13)       // run before the lapse
    }

    @Test
    fun `single lapse day is bridged by a shield`() {
        val today = start.plusDays(20)      // 19 AF completed days -> 2 shields earned
        val lapse = listOf(today.minusDays(5))
        val r = compute(today, lapse)
        assertEquals(19, r.totalAfDays)
        // 4 AF days after the lapse + 15 before it; the bridged day itself doesn't count
        assertEquals(19, r.currentStreak)
        assertEquals(listOf(today.minusDays(5)), r.newlyBridgedDates)
        // 2 earned, 1 consumed
        assertEquals(1, r.shieldsHeld)
    }

    @Test
    fun `no shields means a single lapse ends the streak`() {
        val today = start.plusDays(5)       // only 4 AF days -> 0 shields
        val lapse = listOf(today.minusDays(3))
        val r = compute(today, lapse)
        assertEquals(2, r.currentStreak)
        assertTrue(r.newlyBridgedDates.isEmpty())
        assertEquals(0, r.shieldsHeld)
    }

    @Test
    fun `already bridged dates do not consume again`() {
        val today = start.plusDays(20)
        val lapseDay = today.minusDays(5)
        val first = compute(today, listOf(lapseDay))
        assertEquals(listOf(lapseDay), first.newlyBridgedDates)

        val second = compute(today, listOf(lapseDay), alreadyBridged = setOf(lapseDay))
        assertTrue(second.newlyBridgedDates.isEmpty())
        assertEquals(first.currentStreak, second.currentStreak)
        // held = earned(2) - used(1), not double-charged
        assertEquals(1, second.shieldsHeld)
    }

    @Test
    fun `shields cap at three held`() {
        val today = start.plusDays(40)      // 39 AF days -> 5 earned, cap 3
        val r = compute(today, emptyList())
        assertEquals(3, r.shieldsHeld)
    }

    @Test
    fun `af days this week counts completed days only`() {
        // Friday 2026-07-10; Monday week start
        val today = LocalDate.of(2026, 7, 10)
        val r = compute(today, listOf(LocalDate.of(2026, 7, 7)))
        // Mon 6 Jul .. Thu 9 Jul completed; the 7th was a drink day
        assertEquals(3, r.afDaysThisWeek)
    }

    @Test
    fun `weeks under goal counted over complete weeks`() {
        // start Mon 2026-06-01; today Mon 2026-06-29 -> 4 complete weeks
        val today = LocalDate.of(2026, 6, 29)
        // Week 2 goes heavily over goal (5 x 1000ml with weekly goal 3500)
        val heavyWeek = (0..4).map { LocalDate.of(2026, 6, 8).plusDays(it.toLong()) }
        val entries = heavyWeek.map { TestFixtures.entry(it, volumeMl = 1000.0) }
        val ledger = DayLedger(entries, start, today)
        val r = StreakEngine.compute(ledger, 3500.0, emptySet())
        assertEquals(3, r.totalWeeksUnderGoal)
        assertEquals(2, r.consecutiveWeeksUnderGoal)   // weeks 3 and 4
        assertEquals(2, r.bestConsecutiveWeeksUnderGoal)
    }

    @Test
    fun `growth stage thresholds`() {
        assertEquals(0, StreakEngine.growthStage(0))
        assertEquals(1, StreakEngine.growthStage(3))
        assertEquals(2, StreakEngine.growthStage(7))
        assertEquals(3, StreakEngine.growthStage(30))
        assertEquals(4, StreakEngine.growthStage(90))
    }

    @Test
    fun `first month grows a tree per week then big month trees`() {
        // Trees 1-4: 7 days each (first month); tree 5 onward: 30 days, bigger
        assertEquals(7, StreakEngine.treeCost(0))
        assertEquals(7, StreakEngine.treeCost(3))
        assertEquals(30, StreakEngine.treeCost(4))
        assertEquals(30, StreakEngine.treeCost(9))
        assertFalse(StreakEngine.isBigTree(0))
        assertFalse(StreakEngine.isBigTree(3))
        assertTrue(StreakEngine.isBigTree(4))

        assertEquals(0, StreakEngine.treesCollected(0))
        assertEquals(0, StreakEngine.treesCollected(6))
        assertEquals(1, StreakEngine.treesCollected(7))       // weekly payoffs
        assertEquals(2, StreakEngine.treesCollected(14))
        assertEquals(3, StreakEngine.treesCollected(21))
        assertEquals(4, StreakEngine.treesCollected(28))      // first month done
        assertEquals(4, StreakEngine.treesCollected(57))
        assertEquals(5, StreakEngine.treesCollected(58))      // 28 + 30: first big tree
        assertEquals(0, StreakEngine.treesCollected(-1))      // defensive

        assertEquals(0f, StreakEngine.treeProgress(0), 0.001f)
        assertEquals(3f / 7f, StreakEngine.treeProgress(3), 0.001f)
        assertEquals(0f, StreakEngine.treeProgress(7), 0.001f)    // tree 2 starts
        assertEquals(15f / 30f, StreakEngine.treeProgress(43), 0.001f) // 28 + 15 into big tree

        assertEquals(3, StreakEngine.treeDaysGrown(3))
        assertEquals(7, StreakEngine.treeDaysNeeded(3))
        assertEquals(30, StreakEngine.treeDaysNeeded(28))
        assertEquals(7, StreakEngine.treeCompletionDay(1))
        assertEquals(28, StreakEngine.treeCompletionDay(4))
        assertEquals(58, StreakEngine.treeCompletionDay(5))
    }
}
