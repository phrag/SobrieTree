package com.sobrietree.android.engine

import com.sobrietree.android.BeerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsEngineTest {

    // Wednesday 15 July 2026; the week starts Monday 13th.
    private val wed = LocalDate.of(2026, 7, 15)
    private val weekStart = LocalDate.of(2026, 7, 13)

    private fun drink(date: LocalDate, volumeMl: Double = 500.0, abv: Double = 5.0, tag: String = "") =
        BeerEntry("$date$tag$volumeMl", "Drink", abv, volumeMl, date.toString(), "")

    private fun statsFor(entries: List<BeerEntry>, start: LocalDate, today: LocalDate = wed) =
        StatsEngine.compute(DayLedger(entries, start, today), 500.0, 1000.0)
            .associateBy { it.period }

    @Test
    fun `this week covers only the days that have happened`() {
        val s = statsFor(listOf(drink(weekStart), drink(wed)), weekStart)[StatsEngine.Period.THIS_WEEK]!!
        assertEquals(weekStart, s.start)
        assertEquals(wed, s.end)
        assertEquals(3, s.days)              // Mon, Tue, Wed - not the whole week
        assertEquals(2.0, s.drinks, 0.001)
        assertEquals(1, s.alcoholFreeDays)   // Tuesday
        assertEquals(2, s.drinkingDays)
    }

    @Test
    fun `averages divide by elapsed days, not the calendar`() {
        // 500ml at 5% = 25ml of ethanol = 2.5 units, so two drinks is 5 units
        val s = statsFor(listOf(drink(weekStart), drink(wed)), weekStart)[StatsEngine.Period.THIS_WEEK]!!
        assertEquals(5.0, s.units, 0.001)
        assertEquals(5.0 / 3.0, s.avgUnitsPerDay, 0.001)
        assertEquals(2.0 / 3.0, s.avgDrinksPerDay, 0.001)
    }

    @Test
    fun `last week is a whole finished week`() {
        val lastWeekStart = weekStart.minusDays(7)
        val entries = listOf(drink(lastWeekStart), drink(lastWeekStart.plusDays(3)))
        val s = statsFor(entries, lastWeekStart)[StatsEngine.Period.LAST_WEEK]!!
        assertEquals(lastWeekStart, s.start)
        assertEquals(weekStart.minusDays(1), s.end)
        assertEquals(7, s.days)
        assertEquals(2.0, s.drinks, 0.001)
        assertEquals(5, s.alcoholFreeDays)
    }

    @Test
    fun `a window never reaches back before tracking began`() {
        // Tracking started yesterday, so "last 30 days" is 2 days, not 30.
        val start = wed.minusDays(1)
        val s = statsFor(listOf(drink(wed)), start)[StatsEngine.Period.LAST_30_DAYS]!!
        assertEquals(start, s.start)
        assertEquals(2, s.days)
    }

    @Test
    fun `a window entirely before tracking began reports nothing`() {
        // Tracking started this Monday, so there is no "last week" to report.
        val s = statsFor(listOf(drink(wed)), weekStart)[StatsEngine.Period.LAST_WEEK]!!
        assertEquals(0, s.days)
        assertFalse(s.hasData)
        assertNull(s.vsBaselinePct)
    }

    @Test
    fun `this month starts on the first, or tracking start if later`() {
        val s = statsFor(listOf(drink(wed)), LocalDate.of(2026, 7, 1))[StatsEngine.Period.THIS_MONTH]!!
        assertEquals(LocalDate.of(2026, 7, 1), s.start)
        assertEquals(15, s.days)

        val late = statsFor(listOf(drink(wed)), LocalDate.of(2026, 7, 10))[StatsEngine.Period.THIS_MONTH]!!
        assertEquals(LocalDate.of(2026, 7, 10), late.start)
        assertEquals(6, late.days)
    }

    @Test
    fun `all time runs from the journey start to today`() {
        val start = LocalDate.of(2026, 6, 1)
        val s = statsFor(listOf(drink(start), drink(wed)), start)[StatsEngine.Period.ALL_TIME]!!
        assertEquals(start, s.start)
        assertEquals(wed, s.end)
        assertEquals(45, s.days)
        assertEquals(43, s.alcoholFreeDays)
    }

    @Test
    fun `reduction is measured against the baseline for the same span`() {
        // Baseline 1000ml/day over 3 days = 3000ml. Logged 1000ml -> 66.7% below.
        val s = statsFor(listOf(drink(weekStart), drink(wed)), weekStart)[StatsEngine.Period.THIS_WEEK]!!
        assertEquals(66.67, s.vsBaselinePct!!, 0.01)
    }

    @Test
    fun `drinking above the baseline reports a negative reduction rather than hiding it`() {
        val entries = (0..2).flatMap { day ->
            (0..3).map { drink(weekStart.plusDays(day.toLong()), tag = "$it") }
        }
        val s = statsFor(entries, weekStart)[StatsEngine.Period.THIS_WEEK]!!
        // 12 drinks x 500ml = 6000ml against a 3000ml baseline
        assertTrue(s.vsBaselinePct!! < 0)
        assertEquals(-100.0, s.vsBaselinePct!!, 0.01)
    }

    @Test
    fun `no baseline means no comparison rather than a fake zero`() {
        val stats = StatsEngine.compute(
            DayLedger(listOf(drink(wed)), weekStart, wed), 500.0, baselineDailyMl = 0.0
        ).associateBy { it.period }
        assertNull(stats[StatsEngine.Period.THIS_WEEK]!!.vsBaselinePct)
    }

    @Test
    fun `strength counts, so units and drinks can disagree`() {
        // One 500ml glass of 12% wine: one "drink", but six units.
        val s = statsFor(listOf(drink(wed, abv = 12.0)), weekStart)[StatsEngine.Period.THIS_WEEK]!!
        assertEquals(1.0, s.drinks, 0.001)
        assertEquals(6.0, s.units, 0.001)
    }

    @Test
    fun `every period is returned even when empty`() {
        val stats = StatsEngine.compute(DayLedger(emptyList(), weekStart, wed), 500.0, 1000.0)
        assertEquals(StatsEngine.Period.values().size, stats.size)
        assertTrue(stats.none { it.hasData })
    }
}
