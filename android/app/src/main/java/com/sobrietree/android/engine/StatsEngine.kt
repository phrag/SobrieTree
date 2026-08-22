package com.sobrietree.android.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The same figures over several windows, each carrying the window it covers.
 *
 * A bare "12 drinks" is unreadable without knowing whether that is this week or
 * this year, and a part-finished week compared against a whole one flatters
 * whichever is shorter. Every period therefore reports its own start, end and
 * day count, and averages are taken over the days actually elapsed.
 *
 * Today is included: these are "where am I right now" numbers, unlike the
 * savings counters which only move when a day closes.
 */
object StatsEngine {

    enum class Period { THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_30_DAYS, ALL_TIME }

    data class Stat(
        val period: Period,
        val start: LocalDate,
        val end: LocalDate,
        /** Days in the window that have actually happened, today included. */
        val days: Int,
        val drinks: Double,
        val units: Double,
        val volumeMl: Double,
        /** Days in the window with nothing logged. */
        val alcoholFreeDays: Int,
        val drinkingDays: Int,
        val avgUnitsPerDay: Double,
        val avgDrinksPerDay: Double,
        /** Positive = below the baseline for the same span. Null when no baseline is set. */
        val vsBaselinePct: Double?
    ) {
        val hasData: Boolean get() = volumeMl > 0
    }

    /**
     * @param drinkSizeMl what counts as one drink, for the "drinks" figures
     * @param baselineDailyMl the user's stated usual day; 0 = not set
     */
    fun compute(ledger: DayLedger, drinkSizeMl: Double, baselineDailyMl: Double): List<Stat> {
        val today = ledger.todayEffective
        val thisWeekStart = ledger.weekStartOf(today)
        val lastWeekStart = thisWeekStart.minusDays(7)
        val monthStart = today.withDayOfMonth(1)

        return listOf(
            stat(Period.THIS_WEEK, thisWeekStart, today, ledger, drinkSizeMl, baselineDailyMl),
            stat(Period.LAST_WEEK, lastWeekStart, thisWeekStart.minusDays(1), ledger, drinkSizeMl, baselineDailyMl),
            stat(Period.THIS_MONTH, monthStart, today, ledger, drinkSizeMl, baselineDailyMl),
            stat(Period.LAST_30_DAYS, today.minusDays(29), today, ledger, drinkSizeMl, baselineDailyMl),
            stat(Period.ALL_TIME, ledger.trackingStart, today, ledger, drinkSizeMl, baselineDailyMl)
        )
    }

    private fun stat(
        period: Period,
        rawStart: LocalDate,
        rawEnd: LocalDate,
        ledger: DayLedger,
        drinkSizeMl: Double,
        baselineDailyMl: Double
    ): Stat {
        // Never claim to cover days before tracking began or after today.
        val start = maxOf(rawStart, ledger.trackingStart)
        val end = minOf(rawEnd, ledger.todayEffective)
        if (end < start) {
            return Stat(period, rawStart, rawEnd, 0, 0.0, 0.0, 0.0, 0, 0, 0.0, 0.0, null)
        }

        val days = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        var volume = 0.0
        var units = 0.0
        var drinkingDays = 0
        var d = start
        while (d <= end) {
            val dayVolume = ledger.totalFor(d)
            volume += dayVolume
            units += ledger.unitsFor(d)
            if (dayVolume > 0) drinkingDays++
            d = d.plusDays(1)
        }

        val drinks = if (drinkSizeMl > 0) volume / drinkSizeMl else 0.0
        val baselineForSpan = baselineDailyMl * days
        return Stat(
            period = period,
            start = start,
            end = end,
            days = days,
            drinks = drinks,
            units = units,
            volumeMl = volume,
            alcoholFreeDays = days - drinkingDays,
            drinkingDays = drinkingDays,
            avgUnitsPerDay = units / days,
            avgDrinksPerDay = drinks / days,
            vsBaselinePct = if (baselineForSpan > 0) ((baselineForSpan - volume) / baselineForSpan) * 100.0 else null
        )
    }
}
