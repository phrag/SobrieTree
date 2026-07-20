package com.brewlog.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingsEngineTest {

    private val start = LocalDate.of(2026, 7, 1)
    private val today = LocalDate.of(2026, 7, 10)   // 9 completed days; today excluded

    @Test
    fun `money saved over completed days only`() {
        // Baseline 2 drinks/day (1000ml), drank 5 drinks on completed days, price 4.0
        val entries = (0..4).map { TestFixtures.entry(start.plusDays(it.toLong())) }
        val ledger = DayLedger(entries, start, today)
        val r = SavingsEngine.compute(entries, ledger, 1000.0, 5.0, 500.0, 4.0)
        // expected 2*9=18 drinks, actual 5 -> save 13 * 4 = 52
        assertEquals(52.0, r.moneySaved, 0.001)
        assertTrue(r.moneyAvailable)
    }

    @Test
    fun `todays entries are excluded until the day closes`() {
        val entries = listOf(TestFixtures.entry(today))            // only today
        val ledger = DayLedger(entries, start, today)
        val r = SavingsEngine.compute(entries, ledger, 1000.0, 5.0, 500.0, 4.0)
        assertEquals(0.0, r.moneySpent, 0.001)                     // not counted yet
        // expected 18 drinks over 9 completed days, none logged -> full savings
        assertEquals(72.0, r.moneySaved, 0.001)
    }

    @Test
    fun `day one shows zero not phantom savings`() {
        val ledger = DayLedger(emptyList(), start, start)          // no completed days yet
        val r = SavingsEngine.compute(emptyList(), ledger, 1000.0, 5.0, 500.0, 4.0)
        assertEquals(0.0, r.moneySaved, 0.001)
        assertEquals(0.0, r.caloriesSaved, 0.001)
    }

    @Test
    fun `money hidden without any price`() {
        val ledger = DayLedger(emptyList(), start, today)
        val r = SavingsEngine.compute(emptyList(), ledger, 1000.0, 5.0, 500.0, 0.0)
        assertFalse(r.moneyAvailable)
        assertEquals(0.0, r.moneySaved, 0.001)
    }

    @Test
    fun `savings floored at zero when drinking above baseline`() {
        val entries = (0..8).flatMap { day ->
            (0..3).map { TestFixtures.entry(start.plusDays(day.toLong())) }
        }
        val ledger = DayLedger(entries, start, today)
        val r = SavingsEngine.compute(entries, ledger, 500.0, 5.0, 500.0, 4.0)
        assertEquals(0.0, r.moneySaved, 0.001)
        assertEquals(0.0, r.caloriesSaved, 0.001)
    }

    @Test
    fun `calories saved computed from abv over completed days`() {
        val ledger = DayLedger(emptyList(), start, today)
        val r = SavingsEngine.compute(emptyList(), ledger, 1000.0, 5.0, 500.0, 0.0)
        // expected kcal = 1000 * 0.05 * 0.789*7 * 9 days = 2485.35, actual 0
        assertEquals(2485.35, r.caloriesSaved, 0.5)
        assertEquals(4, r.burgersEquivalent)
    }

    @Test
    fun `preset cost wins over fallback price for matching entries`() {
        val ipa = com.brewlog.android.BeerEntry("1", "IPA", 6.5, 330.0, start.toString(), "")
        val costs = listOf(SavingsEngine.DrinkCost("IPA", 330.0, 7.5))
        // Matched by name+volume: exact preset cost
        assertEquals(7.5, SavingsEngine.entryCost(ipa, costs, 500.0, 4.0), 0.001)
        // Unmatched entry: global price scaled by volume (750/500 * 4)
        val wine = com.brewlog.android.BeerEntry("2", "Wine", 12.0, 750.0, start.toString(), "")
        assertEquals(6.0, SavingsEngine.entryCost(wine, costs, 500.0, 4.0), 0.001)
        // No global price: falls back to average preset cost scaled by volume (750/500 * 7.5)
        assertEquals(11.25, SavingsEngine.entryCost(wine, costs, 500.0, 0.0), 0.001)
        // No price info at all: zero
        assertEquals(0.0, SavingsEngine.entryCost(wine, emptyList(), 500.0, 0.0), 0.001)
    }

    @Test
    fun `spend and savings use per-preset costs`() {
        // Baseline 2 drinks/day priced at the favorite's cost of 5.0 -> expected 90 over 9 days
        val entries = (0..3).map {
            com.brewlog.android.BeerEntry("$it", "IPA", 6.5, 330.0, start.plusDays(it.toLong()).toString(), "")
        }
        val ledger = DayLedger(entries, start, today)
        val costs = listOf(SavingsEngine.DrinkCost("IPA", 330.0, 7.5))
        val r = SavingsEngine.compute(
            entries, ledger, 1000.0, 5.0, 500.0,
            pricePerDrink = 0.0, presetCosts = costs, baselineCostPerDrink = 5.0
        )
        assertEquals(30.0, r.moneySpent, 0.001)          // 4 x 7.5
        assertEquals(60.0, r.moneySaved, 0.001)          // 90 expected - 30 spent
        assertTrue(r.moneyAvailable)
    }

    @Test
    fun `preset costs alone power the whole calculation`() {
        val ledger = DayLedger(emptyList(), start, today)
        val costs = listOf(SavingsEngine.DrinkCost("IPA", 330.0, 7.5))
        val r = SavingsEngine.compute(
            emptyList(), ledger, 1000.0, 5.0, 500.0,
            pricePerDrink = 0.0, presetCosts = costs, baselineCostPerDrink = 0.0
        )
        assertTrue(r.moneyAvailable)
        // Baseline cost falls back to the average preset cost: 2 drinks * 9 days * 7.5
        assertEquals(135.0, r.moneySaved, 0.001)
    }
}
