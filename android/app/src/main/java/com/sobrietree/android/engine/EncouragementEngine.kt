package com.sobrietree.android.engine

import java.time.LocalDate

enum class EncourageState {
    AF_TODAY, UNDER_GOAL, AT_GOAL, OVER_GOAL, LAPSE_RECOVERY, MILESTONE_NEAR
}

/**
 * Picks the tone for the home screen's status line. Messages rotate
 * deterministically by date so the copy feels alive without churning
 * on every refresh. The OVER_GOAL pool is strictly compassionate -
 * a heavy day is met with a plan, never a scolding.
 */
object EncouragementEngine {

    fun state(
        isTodayAfSoFar: Boolean,
        todayMl: Double,
        effectiveDailyGoalMl: Double,
        yesterdayOverGoal: Boolean,
        daysToNextBadge: Int?
    ): EncourageState = when {
        effectiveDailyGoalMl > 0 && todayMl > effectiveDailyGoalMl -> EncourageState.OVER_GOAL
        yesterdayOverGoal && isTodayAfSoFar -> EncourageState.LAPSE_RECOVERY
        isTodayAfSoFar && daysToNextBadge != null && daysToNextBadge <= 2 -> EncourageState.MILESTONE_NEAR
        isTodayAfSoFar -> EncourageState.AF_TODAY
        effectiveDailyGoalMl > 0 && todayMl >= effectiveDailyGoalMl * 0.9 -> EncourageState.AT_GOAL
        else -> EncourageState.UNDER_GOAL
    }

    val pools: Map<EncourageState, List<String>> = mapOf(
        EncourageState.AF_TODAY to listOf(
            "Nothing logged today - your ring is full and your body is repairing.",
            "An alcohol-free day in progress. Sleep tonight will thank you.",
            "Clear head, full ring. This is the good stuff.",
            "Every alcohol-free day lowers the pull of the habit a little more.",
            "Your liver is using today to catch up. Keep it rolling.",
            "Today counts double: no alcohol, and proof you can."
        ),
        EncourageState.UNDER_GOAL to listOf(
            "Comfortably under your goal. Steady beats perfect.",
            "You're pacing this well - room to spare today.",
            "Under goal and in control. That's the whole method.",
            "Nice pacing. A glass of water between drinks stretches it further.",
            "Well within your plan today. You set the limit; you're keeping it.",
            "On track. Small margins add up to big change."
        ),
        EncourageState.AT_GOAL to listOf(
            "You've reached today's goal - a good place to stop.",
            "Goal reached. Calling it here keeps the streak of good weeks alive.",
            "That's the plan done for today. Water from here on?",
            "Right at your limit - stopping now is the win.",
            "Today's allowance is used. Tomorrow starts fresh.",
            "You hit the line you drew. Respect it and it gets easier."
        ),
        EncourageState.OVER_GOAL to listOf(
            "Logged. Tomorrow is a clean page - that's the whole method.",
            "Over today's goal, and you still logged it. Honesty is progress.",
            "One heavy day doesn't undo your trend. The next entry matters more.",
            "No judgment - just data. Your average is what changes your health.",
            "Today went over. An alcohol-free tomorrow balances the week.",
            "It happens. What matters is the week, and the week is still yours."
        ),
        EncourageState.LAPSE_RECOVERY to listOf(
            "Back on track today - that bounce-back is the real skill.",
            "Yesterday is logged and done. Today is already going better.",
            "Recovering a plan beats never slipping. Nothing logged today - keep going.",
            "This is what progress actually looks like: a wobble, then a comeback.",
            "Clean slate today. Your body recovers fast when you give it the chance.",
            "One day at a time works. Today is that day."
        ),
        EncourageState.MILESTONE_NEAR to listOf(
            "One more good day and your next badge is yours.",
            "You're a day or two from a new badge - keep today easy.",
            "A new badge is waiting just ahead. Tonight, water wins.",
            "Almost at your next milestone - today is the last stretch.",
            "Your next badge unlocks this week, and today counts toward it.",
            "So close now. One quiet evening at a time."
        )
    )

    fun message(state: EncourageState, date: LocalDate): String {
        val pool = pools.getValue(state)
        val index = ((date.toEpochDay() % pool.size) + pool.size).toInt() % pool.size
        return pool[index]
    }
}
