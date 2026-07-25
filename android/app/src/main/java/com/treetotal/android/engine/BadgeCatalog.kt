package com.treetotal.android.engine

enum class BadgeKind {
    /** Any entry ever logged - self-monitoring is the first win. */
    FIRST_LOG,
    /** Total (cumulative) alcohol-free days. */
    AF_TOTAL,
    /** Best alcohol-free streak. */
    STREAK,
    /** Complete weeks at or under the weekly goal (total for threshold 1, best run otherwise). */
    WEEKS_UNDER_GOAL,
    /** Money saved in the user's currency. */
    MONEY_SAVED
}

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val kind: BadgeKind,
    val threshold: Int
)

/**
 * All badges are cumulative or additive - none can ever be lost.
 */
object BadgeCatalog {
    val all: List<Badge> = listOf(
        Badge("first_log", "First Log", "You logged a drink. Tracking is how change starts.", BadgeKind.FIRST_LOG, 1),
        Badge("first_af", "Fresh Start", "Your first alcohol-free day. Your body noticed.", BadgeKind.AF_TOTAL, 1),
        Badge("af_2", "Two Days Clear", "2 alcohol-free days in total.", BadgeKind.AF_TOTAL, 2),
        Badge("af_3", "Three & Free", "3 alcohol-free days in total.", BadgeKind.AF_TOTAL, 3),
        Badge("af_5", "High Five", "5 alcohol-free days in total.", BadgeKind.AF_TOTAL, 5),
        Badge("af_7", "Week of Clarity", "7 alcohol-free days in total - deeper sleep territory.", BadgeKind.AF_TOTAL, 7),
        Badge("af_10", "Double Digits", "10 alcohol-free days in total.", BadgeKind.AF_TOTAL, 10),
        Badge("af_14", "Fourteen Strong", "14 alcohol-free days in total.", BadgeKind.AF_TOTAL, 14),
        Badge("af_21", "Three Weeks In", "21 alcohol-free days - past the hardest stretch.", BadgeKind.AF_TOTAL, 21),
        Badge("af_30", "Thirty & Thriving", "30 alcohol-free days in total - blood pressure says thanks.", BadgeKind.AF_TOTAL, 30),
        Badge("af_60", "Sixty Renewed", "60 alcohol-free days in total.", BadgeKind.AF_TOTAL, 60),
        Badge("af_90", "Ninety - New Normal", "90 alcohol-free days in total. This is who you are now.", BadgeKind.AF_TOTAL, 90),
        Badge("streak_3", "Momentum", "3 alcohol-free days in a row.", BadgeKind.STREAK, 3),
        Badge("streak_5", "Five in a Row", "5 alcohol-free days in a row.", BadgeKind.STREAK, 5),
        Badge("streak_7", "Full Week Streak", "7 alcohol-free days in a row.", BadgeKind.STREAK, 7),
        Badge("streak_14", "Two-Week Streak", "14 alcohol-free days in a row.", BadgeKind.STREAK, 14),
        Badge("week_under_goal", "Steady Week", "A full week at or under your goal.", BadgeKind.WEEKS_UNDER_GOAL, 1),
        Badge("month_under_goal", "Month of Balance", "Four weeks in a row at or under your goal.", BadgeKind.WEEKS_UNDER_GOAL, 4),
        Badge("saver_50", "Smart Saver", "50 saved by drinking less.", BadgeKind.MONEY_SAVED, 50),
        Badge("saver_100", "Hundred Back", "100 saved. Real money, back in your pocket.", BadgeKind.MONEY_SAVED, 100),
        Badge("saver_500", "Money in the Bank", "500 saved by drinking less - that's a weekend away.", BadgeKind.MONEY_SAVED, 500)
    )

    fun byId(id: String): Badge? = all.firstOrNull { it.id == id }
}

object BadgeEngine {

    data class Inputs(
        val hasAnyEntry: Boolean,
        val totalAfDays: Int,
        val bestStreak: Int,
        val totalWeeksUnderGoal: Int,
        val bestConsecutiveWeeksUnderGoal: Int,
        val moneySaved: Double
    )

    fun currentValue(badge: Badge, inputs: Inputs): Int = when (badge.kind) {
        BadgeKind.FIRST_LOG -> if (inputs.hasAnyEntry) 1 else 0
        BadgeKind.AF_TOTAL -> inputs.totalAfDays
        BadgeKind.STREAK -> inputs.bestStreak
        BadgeKind.WEEKS_UNDER_GOAL ->
            if (badge.threshold <= 1) inputs.totalWeeksUnderGoal else inputs.bestConsecutiveWeeksUnderGoal
        BadgeKind.MONEY_SAVED -> inputs.moneySaved.toInt()
    }

    fun isEarned(badge: Badge, inputs: Inputs): Boolean = currentValue(badge, inputs) >= badge.threshold

    /** Badges newly crossed, excluding ones already persisted as earned. */
    fun evaluate(inputs: Inputs, alreadyEarned: Set<String>): List<Badge> =
        BadgeCatalog.all.filter { it.id !in alreadyEarned && isEarned(it, inputs) }

    /** Short hint shown under a locked badge, e.g. "5 of 7 AF days". */
    fun progressHint(badge: Badge, inputs: Inputs): String {
        val current = currentValue(badge, inputs).coerceAtMost(badge.threshold)
        return when (badge.kind) {
            BadgeKind.FIRST_LOG -> "Log your first drink"
            BadgeKind.AF_TOTAL -> "$current of ${badge.threshold} AF days"
            BadgeKind.STREAK -> "Best streak $current of ${badge.threshold}"
            BadgeKind.WEEKS_UNDER_GOAL ->
                if (badge.threshold <= 1) "Finish a week under goal"
                else "$current of ${badge.threshold} weeks in a row"
            BadgeKind.MONEY_SAVED -> "$current of ${badge.threshold} saved"
        }
    }
}
