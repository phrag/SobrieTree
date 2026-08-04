package com.treetotal.android.engine

import com.treetotal.android.BeerEntry
import java.time.LocalDate
import kotlin.math.abs

/**
 * Money and calories not consumed compared to the user's baseline.
 * Both are floored at zero: the counters encourage, they never accuse.
 *
 * Only *completed* days count - the in-progress day is excluded from both the
 * expected and the actual side, so savings never appear out of thin air the
 * moment a new day starts; numbers move when a day closes.
 *
 * Money saved is anchored to what the user says they *used* to spend: when a
 * baseline weekly spend is set, expected spend is simply that figure pro-rated
 * over the tracked days — a number the user trusts, independent of fluctuating
 * per-drink prices (a pub round vs. a cheap beer at home). Actual spend still
 * comes from logged drink costs. Without a weekly figure it falls back to the
 * older per-drink-price model. Both saved counters are floored at zero.
 */
object SavingsEngine {

    /** kcal per ml of pure alcohol: 0.789 g/ml x 7 kcal/g. */
    private const val KCAL_PER_ML_ALCOHOL = 0.789 * 7.0

    /** A preset's cost, matched to entries by name + volume. */
    data class DrinkCost(val name: String, val volumeMl: Double, val cost: Double)

    data class Result(
        val moneySaved: Double,
        /** What was actually spent on logged drinks over completed days. */
        val moneySpent: Double,
        /** What the baseline implies you'd have spent over the same days. */
        val moneyExpected: Double,
        /** False until the user has set any price (per-preset or global). */
        val moneyAvailable: Boolean,
        val caloriesSaved: Double,
        /** Rough burger equivalents of the saved calories (550 kcal each). */
        val burgersEquivalent: Int
    )

    /** Average cost of the presets that have one, or 0 when none do. */
    fun averagePresetCost(presetCosts: List<DrinkCost>): Double {
        val priced = presetCosts.filter { it.cost > 0 }
        return if (priced.isEmpty()) 0.0 else priced.sumOf { it.cost } / priced.size
    }

    /**
     * Cost of a single logged entry: matched preset cost, else the global
     * price scaled by volume, else the average preset cost scaled by volume.
     */
    fun entryCost(
        entry: BeerEntry,
        presetCosts: List<DrinkCost>,
        drinkSizeMl: Double,
        pricePerDrink: Double
    ): Double {
        val preset = presetCosts.firstOrNull {
            it.cost > 0 && it.name.equals(entry.name, ignoreCase = true) && abs(it.volumeMl - entry.volumeMl) < 1.0
        }
        if (preset != null) return preset.cost
        val perDrink = if (pricePerDrink > 0) pricePerDrink else averagePresetCost(presetCosts)
        return if (perDrink > 0 && drinkSizeMl > 0) perDrink * (entry.volumeMl / drinkSizeMl) else 0.0
    }

    fun compute(
        entries: List<BeerEntry>,
        ledger: DayLedger,
        baselineDailyMl: Double,
        defaultAbv: Double,
        drinkSizeMl: Double,
        pricePerDrink: Double,
        presetCosts: List<DrinkCost> = emptyList(),
        /** Cost of one baseline drink; callers pass the favorite preset's cost when set. */
        baselineCostPerDrink: Double = pricePerDrink,
        /** What the user says they used to spend on alcohol per week; preferred when > 0. */
        baselineWeeklySpend: Double = 0.0
    ): Result {
        // Completed days only - today joins the ledger when it closes.
        val daysTracked = ledger.completedDays.size

        val effectiveBaselineCost = if (baselineCostPerDrink > 0) baselineCostPerDrink else averagePresetCost(presetCosts)
        val usesWeeklySpend = baselineWeeklySpend > 0
        val moneyAvailable = usesWeeklySpend ||
            (drinkSizeMl > 0 && (effectiveBaselineCost > 0 || pricePerDrink > 0 || presetCosts.any { it.cost > 0 }))

        var actualSpend = 0.0
        var actualKcal = 0.0
        for (entry in entries) {
            if (entry.alcoholPercentage <= 0) continue
            val date = try { LocalDate.parse(entry.date) } catch (_: Exception) { continue }
            if (date < ledger.trackingStart || date >= ledger.todayEffective) continue
            actualSpend += entryCost(entry, presetCosts, drinkSizeMl, pricePerDrink)
            actualKcal += entry.volumeMl * (entry.alcoholPercentage / 100.0) * KCAL_PER_ML_ALCOHOL
        }

        // Prefer the user's stated weekly spend, pro-rated over the tracked days;
        // otherwise fall back to the per-drink-price estimate.
        val expectedSpend = when {
            usesWeeklySpend -> (baselineWeeklySpend / 7.0) * daysTracked
            effectiveBaselineCost > 0 && drinkSizeMl > 0 ->
                (baselineDailyMl / drinkSizeMl) * effectiveBaselineCost * daysTracked
            else -> 0.0
        }
        val moneySaved = if (moneyAvailable && expectedSpend > 0) {
            (expectedSpend - actualSpend).coerceAtLeast(0.0)
        } else 0.0

        val expectedKcal = baselineDailyMl * (defaultAbv / 100.0) * KCAL_PER_ML_ALCOHOL * daysTracked
        val caloriesSaved = (expectedKcal - actualKcal).coerceAtLeast(0.0)

        return Result(
            moneySaved = moneySaved,
            moneySpent = actualSpend,
            moneyExpected = expectedSpend,
            moneyAvailable = moneyAvailable,
            caloriesSaved = caloriesSaved,
            burgersEquivalent = (caloriesSaved / 550.0).toInt()
        )
    }
}
