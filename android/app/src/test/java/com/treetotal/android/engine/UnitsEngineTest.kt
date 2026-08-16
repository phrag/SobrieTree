package com.treetotal.android.engine

import com.treetotal.android.BeerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UnitsEngineTest {

    // Monday, so the week starts here under the default MONDAY week start.
    private val monday = LocalDate.of(2026, 7, 6)

    private fun drink(date: LocalDate, volumeMl: Double, abv: Double, id: String = date.toString()) =
        BeerEntry(id + volumeMl + abv, "Drink", abv, volumeMl, date.toString(), "")

    @Test
    fun `unit conversion matches the published examples`() {
        // NHS worked examples: 1 unit = 10ml pure ethanol.
        // Single 25ml measure of 40% spirit = 1 unit
        assertEquals(1.0, UnitsEngine.unitsIn(25.0, 40.0), 0.001)
        // Pint (568ml) of 4% lager = 2.3 units
        assertEquals(2.27, UnitsEngine.unitsIn(568.0, 4.0), 0.01)
        // Large 250ml glass of 12% wine = 3 units
        assertEquals(3.0, UnitsEngine.unitsIn(250.0, 12.0), 0.001)
        // 330ml bottle of 5% beer = 1.65 units
        assertEquals(1.65, UnitsEngine.unitsIn(330.0, 5.0), 0.001)
    }

    @Test
    fun `zero and nonsense inputs produce no units`() {
        assertEquals(0.0, UnitsEngine.unitsIn(500.0, 0.0), 0.001)
        assertEquals(0.0, UnitsEngine.unitsIn(0.0, 5.0), 0.001)
        assertEquals(0.0, UnitsEngine.unitsIn(-500.0, 5.0), 0.001)
    }

    @Test
    fun `strength matters, not just volume`() {
        // The whole point of units: equal volumes, very different alcohol.
        val lager = UnitsEngine.unitsIn(500.0, 4.0)
        val wine = UnitsEngine.unitsIn(500.0, 12.0)
        assertEquals(2.0, lager, 0.001)
        assertEquals(6.0, wine, 0.001)
        assertEquals(3.0, wine / lager, 0.001)
    }

    @Test
    fun `week under the guideline is reported as within it`() {
        // 4 pints of 4% across the week = 9.1 units
        val entries = (0..3).map { drink(monday.plusDays(it.toLong()), 568.0, 4.0) }
        val ledger = DayLedger(entries, monday, monday.plusDays(4))
        val r = UnitsEngine.compute(ledger)
        assertEquals(9.09, r.unitsThisWeek, 0.01)
        assertTrue(r.withinGuideline)
        assertEquals(0.649, r.ratioOfGuideline, 0.01)
        assertEquals(4, r.drinkingDaysThisWeek)
        assertEquals(1, r.drinkFreeDaysThisWeek)   // 5 days elapsed, 4 with a drink
        assertTrue(r.hasUnitData)
    }

    @Test
    fun `week over the guideline is reported as over`() {
        // 6 large glasses of 12% wine = 18 units
        val entries = (0..5).map { drink(monday.plusDays(it.toLong()), 250.0, 12.0) }
        val ledger = DayLedger(entries, monday, monday.plusDays(6))
        val r = UnitsEngine.compute(ledger)
        assertEquals(18.0, r.unitsThisWeek, 0.001)
        assertFalse(r.withinGuideline)
        assertTrue(r.ratioOfGuideline > 1.0)
        assertFalse(r.concentratedDrinking)        // spread over 6 days, so not concentrated
    }

    @Test
    fun `a heavy week squeezed into two days is flagged as concentrated`() {
        // The guidance singles this out: 14+ units over fewer than 3 days.
        val entries = listOf(
            drink(monday, 750.0, 12.0, "a"),               // 9 units
            drink(monday.plusDays(1), 750.0, 12.0, "b")    // 9 units
        )
        val ledger = DayLedger(entries, monday, monday.plusDays(2))
        val r = UnitsEngine.compute(ledger)
        assertEquals(18.0, r.unitsThisWeek, 0.001)
        assertEquals(2, r.drinkingDaysThisWeek)
        assertTrue(r.concentratedDrinking)
    }

    @Test
    fun `the same units spread over three days is not flagged`() {
        val entries = (0..2).map { drink(monday.plusDays(it.toLong()), 500.0, 12.0) }  // 6 units each
        val ledger = DayLedger(entries, monday, monday.plusDays(3))
        val r = UnitsEngine.compute(ledger)
        assertEquals(18.0, r.unitsThisWeek, 0.001)
        assertEquals(3, r.drinkingDaysThisWeek)
        assertFalse(r.concentratedDrinking)
    }

    @Test
    fun `a dry week reports no units but still counts its dry days`() {
        val ledger = DayLedger(emptyList(), monday, monday.plusDays(3))
        val r = UnitsEngine.compute(ledger)
        assertEquals(0.0, r.unitsThisWeek, 0.001)
        assertTrue(r.withinGuideline)
        assertEquals(4, r.drinkFreeDaysThisWeek)
        assertEquals(0, r.drinkingDaysThisWeek)
        assertFalse(r.hasUnitData)
    }

    @Test
    fun `average per week uses completed days including dry ones`() {
        // One 2-unit drink on each of the first 2 days of a 4-day closed window.
        val entries = (0..1).map { drink(monday.plusDays(it.toLong()), 500.0, 4.0) }
        val ledger = DayLedger(entries, monday, monday.plusDays(4))
        val r = UnitsEngine.compute(ledger)
        // 4 units over 4 closed days = 1/day = 7/week
        assertEquals(7.0, r.avgUnitsPerWeek, 0.001)
    }

    @Test
    fun `last week is reported separately once one has closed`() {
        val lastMonday = monday.minusDays(7)
        val entries = listOf(
            drink(lastMonday, 568.0, 4.0, "prev"),   // 2.27 units last week
            drink(monday, 250.0, 12.0, "curr")       // 3 units this week
        )
        val ledger = DayLedger(entries, lastMonday, monday.plusDays(1))
        val r = UnitsEngine.compute(ledger)
        assertEquals(3.0, r.unitsThisWeek, 0.001)
        assertEquals(2.27, r.unitsLastWeek, 0.01)
    }

    @Test
    fun `ledger keeps volume and units as separate measures`() {
        // Equal volume, different strength: volume totals match, units do not.
        val lagerDay = DayLedger(listOf(drink(monday, 500.0, 4.0)), monday, monday.plusDays(1))
        val wineDay = DayLedger(listOf(drink(monday, 500.0, 12.0)), monday, monday.plusDays(1))
        assertEquals(lagerDay.totalFor(monday), wineDay.totalFor(monday), 0.001)
        assertEquals(2.0, lagerDay.unitsFor(monday), 0.001)
        assertEquals(6.0, wineDay.unitsFor(monday), 0.001)
    }
}
