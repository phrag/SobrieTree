package com.brewlog.android

import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek

enum class TipContext {
    MORNING,
    AFTERNOON,
    EVENING,
    WEEKEND,
    AFTER_LOGGING,
    HIGH_CONSUMPTION_DAY,
    APPROACHING_LIMIT,
    ON_STREAK,
    GENERAL
}

data class TipResult(
    val tip: String,
    val context: TipContext
)

object ContextualTips {

    // ========== REDUCE PATH TIPS ==========

    private val reduceMorningTips = listOf(
        "Plan your drinks for today. Having a number in mind helps.",
        "Hydrate first thing. It sets a healthy tone for the day.",
        "Think about your triggers today. Awareness is preparation.",
        "A clear morning is a gift from yesterday's moderation."
    )

    private val reduceAfternoonTips = listOf(
        "Afternoon energy dip? A walk works better than a drink.",
        "If you're drinking tonight, eat a solid meal first.",
        "Check in with yourself: how are you feeling right now?",
        "Stay hydrated. It makes moderation easier later."
    )

    private val reduceEveningTips = listOf(
        "Craving a drink? Try waiting 10 minutes. Cravings often pass.",
        "Alternate alcoholic drinks with water or sparkling water.",
        "Pour smaller servings. Your brain won't notice the difference.",
        "Set a cutoff time for your last drink tonight.",
        "Ask yourself: am I drinking out of habit or genuine desire?"
    )

    private val reduceWeekendTips = listOf(
        "Social situations can be tricky. Have a plan before you go.",
        "Pace yourself. There's no rush to finish your drink.",
        "Try ordering a mocktail or low-alcohol option first.",
        "Weekend doesn't mean 'drink more'. It means 'rest more'.",
        "Plan at least one alcohol-free activity this weekend."
    )

    private val reduceAfterLoggingTips = listOf(
        "Good job tracking. Awareness is power.",
        "Every drink logged is a step toward understanding your patterns.",
        "Tracking helps you see the real picture. Keep it up."
    )

    private val reduceOnStreakTips = listOf(
        "Your moderation streak is building. Each day gets easier.",
        "Notice how you feel when you drink less. Remember that.",
        "You're proving that lighter drinking is possible for you."
    )

    private val reduceHighConsumptionTips = listOf(
        "Higher day? Tomorrow is a chance to balance it out.",
        "No judgment. Just awareness. What triggered today?",
        "One heavy day doesn't undo your progress. Stay the course."
    )

    private val reduceGeneralTips = listOf(
        "Progress over perfection. Small reductions matter.",
        "Your body thanks you for every drink you skip.",
        "Moderation is a skill. You're practicing it."
    )

    // ========== STOP PATH TIPS ==========

    private val stopMorningTips = listOf(
        "Another sober morning ahead. You've got this.",
        "Plan your day. Idle time can lead to cravings.",
        "Check in with your support system today.",
        "Remember why you're doing this. Write it down if it helps."
    )

    private val stopAfternoonTips = listOf(
        "Afternoon can be a vulnerable time. Stay busy.",
        "If cravings hit, call someone or go for a walk.",
        "You're past the halfway point of the day. Keep going.",
        "Eat something nourishing. Hunger can mimic cravings."
    )

    private val stopEveningTips = listOf(
        "Evenings can be the hardest. Have a plan ready.",
        "Replace your drinking ritual with something else.",
        "Play the tape forward: how will you feel tomorrow if you drink?",
        "One evening at a time. That's all you need to handle.",
        "The craving will pass whether you drink or not."
    )

    private val stopWeekendTips = listOf(
        "Weekends without alcohol are possible. Plan activities.",
        "Social pressure is real. Have your response ready.",
        "Fill your time with things you couldn't do while drinking.",
        "Sober weekends become the new normal. Give it time.",
        "You don't owe anyone an explanation for not drinking."
    )

    private val stopAfterLoggingTips = listOf(
        "Logging helps you understand your journey. Keep tracking.",
        "Every entry tells a story. What's yours saying?",
        "Awareness of patterns is crucial. Good job staying mindful."
    )

    private val stopOnStreakTips = listOf(
        "Your sober streak is growing. Be proud of each day.",
        "Every sober day is rewiring your brain for the better.",
        "Your commitment is inspiring. Keep protecting your sobriety."
    )

    private val stopGeneralTips = listOf(
        "Sobriety is freedom, even when it doesn't feel like it.",
        "You're stronger than any craving. Remember that.",
        "One day, one hour, one minute at a time if needed."
    )

    fun getTip(
        goalMode: GoalMode,
        currentTime: LocalTime = LocalTime.now(),
        currentDate: LocalDate = LocalDate.now(),
        justLogged: Boolean = false,
        streakDays: Int = 0,
        consumptionRatio: Double = 0.0
    ): TipResult {
        val hour = currentTime.hour
        val dayOfWeek = currentDate.dayOfWeek
        val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
        val isReduce = goalMode == GoalMode.REDUCE

        // Priority 1: Just logged a drink
        if (justLogged) {
            return TipResult(
                tip = getAfterLoggingTip(goalMode),
                context = TipContext.AFTER_LOGGING
            )
        }

        // Priority 2: High consumption day (reduce mode only)
        if (isReduce && consumptionRatio > 0.8) {
            return TipResult(
                tip = reduceHighConsumptionTips.random(),
                context = TipContext.HIGH_CONSUMPTION_DAY
            )
        }

        // Priority 3: On a streak (3+ days)
        if (streakDays >= 3) {
            val tips = if (isReduce) reduceOnStreakTips else stopOnStreakTips
            return TipResult(
                tip = tips.random(),
                context = TipContext.ON_STREAK
            )
        }

        // Priority 4: Weekend context
        if (isWeekend) {
            val tips = if (isReduce) reduceWeekendTips else stopWeekendTips
            return TipResult(
                tip = tips.random(),
                context = TipContext.WEEKEND
            )
        }

        // Priority 5: Time-based context
        return when {
            hour in 5..11 -> {
                val tips = if (isReduce) reduceMorningTips else stopMorningTips
                TipResult(tip = tips.random(), context = TipContext.MORNING)
            }
            hour in 12..16 -> {
                val tips = if (isReduce) reduceAfternoonTips else stopAfternoonTips
                TipResult(tip = tips.random(), context = TipContext.AFTERNOON)
            }
            hour in 17..23 || hour in 0..4 -> {
                val tips = if (isReduce) reduceEveningTips else stopEveningTips
                TipResult(tip = tips.random(), context = TipContext.EVENING)
            }
            else -> {
                val tips = if (isReduce) reduceGeneralTips else stopGeneralTips
                TipResult(tip = tips.random(), context = TipContext.GENERAL)
            }
        }
    }

    fun getAfterLoggingTip(goalMode: GoalMode): String {
        return if (goalMode == GoalMode.REDUCE) {
            reduceAfterLoggingTips.random()
        } else {
            stopAfterLoggingTips.random()
        }
    }
}
