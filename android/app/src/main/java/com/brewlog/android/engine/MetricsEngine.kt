package com.brewlog.android.engine

/**
 * Consumption vs goal vs baseline, shared by Home and Progress so the two
 * screens finally agree. Replaces the copies that lived in
 * MainActivity.loadData(), ProgressActivity.loadData() and BrewLog.getProgressMetrics().
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
        val overWeeklyGoal: Boolean
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
            overWeeklyGoal = effectiveWeekly > 0 && weekMl > effectiveWeekly
        )
    }
}
