package com.brewlog.android.engine

import com.brewlog.android.BeerEntry
import java.time.LocalDate

/**
 * Money and calories not consumed compared to the user's baseline.
 * Both are floored at zero: the counters encourage, they never accuse.
 */
object SavingsEngine {

    /** kcal per ml of pure alcohol: 0.789 g/ml x 7 kcal/g. */
    private const val KCAL_PER_ML_ALCOHOL = 0.789 * 7.0

    data class Result(
        val moneySaved: Double,
        /** False until the user has set a price per drink. */
        val moneyAvailable: Boolean,
        val caloriesSaved: Double,
        /** Rough burger equivalents of the saved calories (550 kcal each). */
        val burgersEquivalent: Int
    )

    fun compute(
        entries: List<BeerEntry>,
        ledger: DayLedger,
        baselineDailyMl: Double,
        defaultAbv: Double,
        drinkSizeMl: Double,
        pricePerDrink: Double
    ): Result {
        // Include today so counters move from the very first day.
        val daysTracked = ledger.completedDays.size + 1
        val actualMl = ledger.totalForRange(ledger.trackingStart, ledger.todayEffective)

        val moneyAvailable = pricePerDrink > 0 && drinkSizeMl > 0
        val moneySaved = if (moneyAvailable) {
            val expectedDrinks = (baselineDailyMl / drinkSizeMl) * daysTracked
            val actualDrinks = actualMl / drinkSizeMl
            ((expectedDrinks - actualDrinks) * pricePerDrink).coerceAtLeast(0.0)
        } else 0.0

        var actualKcal = 0.0
        for (entry in entries) {
            if (entry.alcoholPercentage <= 0) continue
            val date = try { LocalDate.parse(entry.date) } catch (_: Exception) { continue }
            if (date < ledger.trackingStart || date > ledger.todayEffective) continue
            actualKcal += entry.volumeMl * (entry.alcoholPercentage / 100.0) * KCAL_PER_ML_ALCOHOL
        }
        val expectedKcal = baselineDailyMl * (defaultAbv / 100.0) * KCAL_PER_ML_ALCOHOL * daysTracked
        val caloriesSaved = (expectedKcal - actualKcal).coerceAtLeast(0.0)

        return Result(
            moneySaved = moneySaved,
            moneyAvailable = moneyAvailable,
            caloriesSaved = caloriesSaved,
            burgersEquivalent = (caloriesSaved / 550.0).toInt()
        )
    }
}
