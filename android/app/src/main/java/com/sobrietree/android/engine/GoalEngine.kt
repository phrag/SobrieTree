package com.sobrietree.android.engine

/**
 * Which window the user's goal is set in.
 *
 * Both a daily and a weekly figure are always stored - each one prefills the
 * other - but only one of them is the plan. The mode says which, so the ring,
 * its copy and the stats screen can all talk in the same terms.
 */
enum class GoalMode(val key: String) {
    DAILY("daily"),
    WEEKLY("weekly");

    companion object {
        /**
         * Anything unrecognised reads as [DAILY]: that is what every install
         * from before this setting existed has been running, so a missing or
         * corrupt value must not quietly rewrite someone's plan.
         */
        fun fromKey(key: String?): GoalMode = entries.firstOrNull { it.key == key } ?: DAILY
    }
}

/**
 * The goal as the Home ring shows it: one allowance, how much of it is spent,
 * and how much is left.
 *
 * The ring depletes over whichever window the goal is set in. In daily mode
 * that is the day, which is how the app has always behaved. In weekly mode it
 * is the week, so a heavy Friday visibly spends what is left for Saturday
 * rather than resetting overnight - which is the whole point of choosing a
 * weekly allowance.
 */
object GoalEngine {

    const val DAYS_PER_WEEK = 7

    /** The weekly partner of a daily figure, and back again. Prefill only. */
    fun weeklyFromDaily(dailyMl: Double): Double = dailyMl * DAYS_PER_WEEK

    fun dailyFromWeekly(weeklyMl: Double): Double = weeklyMl / DAYS_PER_WEEK

    data class Ring(
        val mode: GoalMode,
        /** The goal for the active window, in ml. 0 when no goal or baseline is set. */
        val allowanceMl: Double,
        /** Logged so far in the active window. */
        val consumedMl: Double,
        /** Floored at zero: an overspent allowance has nothing left, not a negative amount. */
        val remainingMl: Double,
        /** consumed / allowance, uncapped, so the ring can tell "just over" from "double". */
        val consumedRatio: Double,
        /** Nothing logged in the window yet - the fullest the ring ever renders. */
        val untouched: Boolean,
        val overGoal: Boolean,
        /**
         * Days of the window still to come, today included: always 1 in daily
         * mode, 1..7 in weekly mode. A weekly allowance is only readable
         * alongside the time left to spend it in.
         */
        val daysLeftInWindow: Int
    )

    fun ring(mode: GoalMode, metrics: MetricsEngine.Result): Ring {
        val allowance = when (mode) {
            GoalMode.DAILY -> metrics.effectiveDailyGoalMl
            GoalMode.WEEKLY -> metrics.effectiveWeeklyGoalMl
        }
        val consumed = when (mode) {
            GoalMode.DAILY -> metrics.todayMl
            GoalMode.WEEKLY -> metrics.weekMl
        }
        val daysLeft = when (mode) {
            GoalMode.DAILY -> 1
            GoalMode.WEEKLY ->
                (DAYS_PER_WEEK - metrics.daysElapsedThisWeek + 1).coerceIn(1, DAYS_PER_WEEK)
        }
        return Ring(
            mode = mode,
            allowanceMl = allowance,
            consumedMl = consumed,
            remainingMl = (allowance - consumed).coerceAtLeast(0.0),
            consumedRatio = if (allowance > 0) consumed / allowance else 0.0,
            untouched = consumed == 0.0,
            overGoal = allowance > 0 && consumed > allowance,
            daysLeftInWindow = daysLeft
        )
    }

    /**
     * What to judge a single day by - the week dots, and the nudge that reacts
     * to yesterday.
     *
     * In weekly mode that is an even share of the weekly allowance, not the
     * stored daily figure: the daily one is then only a prefill, and may be a
     * stale number the user never meant to live by.
     */
    fun dailyYardstickMl(mode: GoalMode, metrics: MetricsEngine.Result): Double = when (mode) {
        GoalMode.DAILY -> metrics.effectiveDailyGoalMl
        GoalMode.WEEKLY -> dailyFromWeekly(metrics.effectiveWeeklyGoalMl)
    }
}
