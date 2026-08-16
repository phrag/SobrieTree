package com.treetotal.android.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * UK alcohol units, and how the user's week sits against the UK Chief Medical
 * Officers' low-risk drinking guidelines.
 *
 * The rest of the app measures drink volume, which is what goals and streaks
 * are set in. Health guidance is not written that way: it counts alcohol, so a
 * large glass of wine is worth roughly three times a half of lager. Without
 * this conversion the app can quote the NHS at the user but cannot tell them
 * whether they are inside the advice.
 *
 * Definitions used here, all from the guidance itself:
 *  - 1 UK unit = 10 ml (8 g) of pure ethanol.
 *  - Low-risk guideline: no more than 14 units a week on a regular basis,
 *    for men and women alike.
 *  - If drinking as much as 14 units a week, spread it over 3 or more days;
 *    concentrating it into fewer sessions raises the risk.
 *
 * Sources: UK Chief Medical Officers' Low Risk Drinking Guidelines (2016);
 * NHS, "Alcohol units" and "The risks of drinking too much".
 */
object UnitsEngine {

    /** Millilitres of pure ethanol in one UK unit. */
    const val ML_PER_UNIT = 10.0

    /** The UK CMOs' weekly low-risk threshold, in units. */
    const val WEEKLY_GUIDELINE_UNITS = 14.0

    /** Days the guidance asks you to spread a full 14 units across, at minimum. */
    const val GUIDELINE_SPREAD_DAYS = 3

    /** UK units in a single drink. */
    fun unitsIn(volumeMl: Double, abvPercent: Double): Double =
        if (volumeMl <= 0 || abvPercent <= 0) 0.0
        else volumeMl * (abvPercent / 100.0) / ML_PER_UNIT

    data class Result(
        /** Units logged today so far. */
        val unitsToday: Double,
        /** Units so far in the current week (today included - it is the live figure). */
        val unitsThisWeek: Double,
        /** Units in the last fully completed week, or 0 when there isn't one yet. */
        val unitsLastWeek: Double,
        /** Mean units per week across all completed days, dry days included. */
        val avgUnitsPerWeek: Double,
        /** unitsThisWeek / 14, uncapped. */
        val ratioOfGuideline: Double,
        /** True while this week is at or under 14 units. */
        val withinGuideline: Boolean,
        /** Days so far this week with nothing logged. */
        val drinkFreeDaysThisWeek: Int,
        /** Days so far this week with at least one drink. */
        val drinkingDaysThisWeek: Int,
        /**
         * True when the week is at or over the guideline *and* squeezed into
         * fewer than three days - the pattern the guidance singles out.
         */
        val concentratedDrinking: Boolean,
        /** False until at least one drink with a usable ABV has been logged. */
        val hasUnitData: Boolean
    )

    fun compute(ledger: DayLedger): Result {
        val today = ledger.todayEffective
        val weekStart = ledger.weekStartOf(today)
        val unitsThisWeek = ledger.unitsForRange(weekStart, today)

        val lastWeekStart = weekStart.minusDays(7)
        val unitsLastWeek =
            if (lastWeekStart >= ledger.trackingStart) ledger.unitsForRange(lastWeekStart, weekStart.minusDays(1))
            else 0.0

        val closedDays = ledger.completedDays
        val avgUnitsPerDay =
            if (closedDays.isEmpty()) 0.0
            else closedDays.sumOf { ledger.unitsFor(it) } / closedDays.size

        // Only days that have actually happened this week, so a Tuesday isn't
        // credited with the rest of the week's dry days in advance.
        val daysElapsed = (ChronoUnit.DAYS.between(weekStart, today) + 1).toInt().coerceAtLeast(1)
        var drinkingDays = 0
        var d: LocalDate = weekStart
        while (d <= today) {
            if (ledger.unitsFor(d) > 0) drinkingDays++
            d = d.plusDays(1)
        }

        return Result(
            unitsToday = ledger.unitsFor(today),
            unitsThisWeek = unitsThisWeek,
            unitsLastWeek = unitsLastWeek,
            avgUnitsPerWeek = avgUnitsPerDay * 7.0,
            ratioOfGuideline = unitsThisWeek / WEEKLY_GUIDELINE_UNITS,
            withinGuideline = unitsThisWeek <= WEEKLY_GUIDELINE_UNITS,
            drinkFreeDaysThisWeek = daysElapsed - drinkingDays,
            drinkingDaysThisWeek = drinkingDays,
            concentratedDrinking = unitsThisWeek >= WEEKLY_GUIDELINE_UNITS &&
                drinkingDays in 1 until GUIDELINE_SPREAD_DAYS,
            hasUnitData = ledger.dailyUnits.values.any { it > 0 }
        )
    }
}
