package com.treetotal.android.engine

/**
 * Consumption vs goal vs baseline, shared by Home and Progress so the two
 * screens finally agree. Replaces the copies that lived in
 * MainActivity.loadData(), ProgressActivity.loadData() and TreeTotal.getProgressMetrics().
 */
object MetricsEngine {

    data class Result(
        val todayMl: Double,
        val weekMl: Double,
        val monthMl: Double,
        /** Goal, falling back to the baseline when no explicit goal is set. */
        val effectiveDailyGoalMl: Double,
        val effectiveWeeklyGoalMl: Double,
        val baselineDailyMl: Double,
        val baselineWeeklyMl: Double,
        val baselineMonthlyMl: Double,
        /** Positive = drinking less than baseline. */
        val reductionDailyPct: Double,
        val reductionWeeklyPct: Double,
        val reductionMonthlyPct: Double,
        /** today / effective daily goal, uncapped (may exceed 1). */
        val dailyRatio: Double,
        val overDailyGoal: Boolean,
        val overWeeklyGoal: Boolean,
        /**
         * How much less than a usual week, so far this week. The baseline is
         * pro-rated to the days elapsed, so a Tuesday isn't flattered by being
         * compared against a whole week. Floored at zero.
         */
        val fewerThanUsualThisWeekMl: Double,
        /** Mean per day across every tracked day, dry days included. */
        val avgPerDayAllTimeMl: Double,
        /** The same average expressed weekly, which is how drinking guidance is written. */
        val avgPerWeekAllTimeMl: Double,
        /** False until at least one day has closed, so the UI can show "—" instead of 0. */
        val hasClosedDays: Boolean
    )

    fun compute(
        ledger: DayLedger,
        goalDailyMl: Double,
        goalWeeklyMl: Double,
        baselineDailyMl: Double
    ): Result {
        val today = ledger.todayEffective
        val todayMl = ledger.totalFor(today)
        val weekMl = ledger.totalForRange(ledger.weekStartOf(today), today)
        val monthMl = ledger.totalForRange(today.minusDays(29), today)

        val baselineWeeklyMl = baselineDailyMl * 7.0
        val baselineMonthlyMl = baselineDailyMl * 30.0
        val effectiveDaily = if (goalDailyMl > 0) goalDailyMl else baselineDailyMl
        val effectiveWeekly = if (goalWeeklyMl > 0) goalWeeklyMl else baselineWeeklyMl

        fun reduction(baseline: Double, current: Double): Double =
            if (baseline > 0) ((baseline - current) / baseline) * 100.0 else 0.0

        // Compare like with like: only the days of this week that have happened.
        val weekStart = ledger.weekStartOf(today)
        val daysElapsedThisWeek = (java.time.temporal.ChronoUnit.DAYS.between(weekStart, today) + 1)
            .coerceAtLeast(1L)
        val usualSoFarMl = baselineDailyMl * daysElapsedThisWeek
        val fewerThanUsualThisWeekMl = (usualSoFarMl - weekMl).coerceAtLeast(0.0)

        // Closed days only: today is still in progress and would drag the mean down.
        val closedDays = ledger.completedDays
        val avgPerDayAllTimeMl = if (closedDays.isEmpty()) 0.0
            else closedDays.sumOf { ledger.totalFor(it) } / closedDays.size

        return Result(
            todayMl = todayMl,
            weekMl = weekMl,
            monthMl = monthMl,
            effectiveDailyGoalMl = effectiveDaily,
            effectiveWeeklyGoalMl = effectiveWeekly,
            baselineDailyMl = baselineDailyMl,
            baselineWeeklyMl = baselineWeeklyMl,
            baselineMonthlyMl = baselineMonthlyMl,
            reductionDailyPct = reduction(baselineDailyMl, todayMl),
            reductionWeeklyPct = reduction(baselineWeeklyMl, weekMl),
            reductionMonthlyPct = reduction(baselineMonthlyMl, monthMl),
            dailyRatio = if (effectiveDaily > 0) todayMl / effectiveDaily else 0.0,
            overDailyGoal = effectiveDaily > 0 && todayMl > effectiveDaily,
            overWeeklyGoal = effectiveWeekly > 0 && weekMl > effectiveWeekly,
            fewerThanUsualThisWeekMl = fewerThanUsualThisWeekMl,
            avgPerDayAllTimeMl = avgPerDayAllTimeMl,
            avgPerWeekAllTimeMl = avgPerDayAllTimeMl * 7.0,
            hasClosedDays = closedDays.isNotEmpty()
        )
    }
}
