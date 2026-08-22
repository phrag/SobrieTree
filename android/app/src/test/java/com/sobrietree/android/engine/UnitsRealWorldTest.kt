package com.sobrietree.android.engine

import com.sobrietree.android.BeerEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The published worked examples, end to end through the ledger rather than
 * through [UnitsEngine.unitsIn] alone - a correct formula reached with the
 * wrong ABV still shows the user a wrong number.
 */
class UnitsRealWorldTest {

    private val day = LocalDate.of(2026, 8, 20)

    private fun ledgerWith(vararg drinks: Pair<Double, Double>): DayLedger =
        DayLedger(
            drinks.mapIndexed { i, (vol, abv) ->
                BeerEntry("$i", "Drink", abv, vol, day.toString(), "")
            },
            day,
            day
        )

    @Test
    fun `a 500ml beer at 5 point 2 percent is 2 point 6 units`() {
        assertEquals(2.6, ledgerWith(500.0 to 5.2).unitsFor(day), 0.0001)
    }

    @Test
    fun `the published examples all land where they should`() {
        assertEquals(1.0, ledgerWith(25.0 to 40.0).unitsFor(day), 0.0001)   // single spirit measure
        assertEquals(2.27, ledgerWith(568.0 to 4.0).unitsFor(day), 0.01)    // pint of 4%, "2.3" rounded
        assertEquals(3.0, ledgerWith(250.0 to 12.0).unitsFor(day), 0.0001)  // large glass of wine
        assertEquals(1.65, ledgerWith(330.0 to 5.0).unitsFor(day), 0.0001)  // 330ml bottle of 5%
        assertEquals(9.0, ledgerWith(750.0 to 12.0).unitsFor(day), 0.0001)  // bottle of wine
    }

    @Test
    fun `a day of mixed drinks sums correctly`() {
        // Two 500ml at 5.2% plus a large glass of 12% wine
        val total = ledgerWith(500.0 to 5.2, 500.0 to 5.2, 250.0 to 12.0).unitsFor(day)
        assertEquals(8.2, total, 0.0001)
    }

    @Test
    fun `the week total and today agree for a single day`() {
        val ledger = ledgerWith(500.0 to 5.2)
        val r = UnitsEngine.compute(ledger)
        assertEquals(2.6, r.unitsToday, 0.0001)
        assertEquals(2.6, r.unitsThisWeek, 0.0001)
    }

    @Test
    fun `stats report the same units as the units card`() {
        val ledger = ledgerWith(500.0 to 5.2)
        val week = StatsEngine.compute(ledger, 500.0, 0.0)
            .first { it.period == StatsEngine.Period.THIS_WEEK }
        assertEquals(2.6, week.units, 0.0001)
        assertEquals(1.0, week.drinks, 0.0001)   // one drink, but 2.6 units
    }
}
