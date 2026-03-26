package com.brewlog.android

import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek

enum class GoalMode {
    REDUCE,
    STOP
}

data class MessageContext(
    val currentTime: LocalTime = LocalTime.now(),
    val currentDate: LocalDate = LocalDate.now(),
    val isOnStreak: Boolean = false,
    val streakDays: Int = 0,
    val isUnderBaseline: Boolean = true,
    val reductionPercent: Double = 0.0,
    val goalMode: GoalMode = GoalMode.REDUCE
)

object MotivationalMessages {

    // ========== REDUCE PATH MESSAGES ==========
    // Focus: harm reduction, moderation as skill, lighter drinking

    private val reduceMorningMessages = listOf(
        "Every day is a fresh start.",
        "Good morning. Mindful choices ahead.",
        "Today, drink a little less.",
        "A lighter day starts now.",
        "Small changes, big impact."
    )

    private val reduceEveningMessages = listOf(
        "Be kind to yourself tonight.",
        "Wind down mindfully.",
        "You don't need a drink to relax.",
        "One less tonight is a win.",
        "Evenings are for rest, not regret."
    )

    private val reduceWeekendMessages = listOf(
        "Weekends don't have to mean excess.",
        "Enjoy your time off, lighter.",
        "Balance fun and moderation.",
        "Have a plan for tonight.",
        "You choose your weekend story."
    )

    private val reduceStreakMessages = listOf(
        "Building lighter habits.",
        "Moderation is becoming easier.",
        "Consistent effort, real change.",
        "You're drinking less, feeling more.",
        "Steady progress, well done."
    )

    private val reduceLongStreakMessages = listOf(
        "Impressive control.",
        "Moderation is a skill you've built.",
        "Your body is thanking you.",
        "Lighter drinking, brighter days.",
        "Real change is happening."
    )

    private val reduceProgressMessages = listOf(
        "Every drink you skip makes a difference.",
        "Lighter drinking, lighter mornings.",
        "Your effort is paying off.",
        "You're finding your balance.",
        "Less alcohol, more clarity."
    )

    private val reduceEncouragementMessages = listOf(
        "One day over limit doesn't erase progress.",
        "Tomorrow is a new chance to drink less.",
        "Setbacks happen. Keep going.",
        "Progress isn't always linear.",
        "You're still here, that matters.",
        "Be patient with yourself."
    )

    private val reduceNeutralMessages = listOf(
        "Every drink logged is awareness gained.",
        "Track, reflect, improve.",
        "Moderation is a journey.",
        "You're making thoughtful choices.",
        "One choice at a time."
    )

    // ========== STOP PATH MESSAGES ==========
    // Focus: sobriety milestones, freedom from alcohol, healing

    private val stopMorningMessages = listOf(
        "Another sober morning. Well done.",
        "Clear head, new day.",
        "Waking up without regret.",
        "Your sober streak continues.",
        "Freedom starts each morning."
    )

    private val stopEveningMessages = listOf(
        "You made it through another day sober.",
        "Rest well, you earned it.",
        "Sober nights mean better mornings.",
        "Cravings pass. You're stronger.",
        "Tonight, you choose peace."
    )

    private val stopWeekendMessages = listOf(
        "Sober weekends are possible.",
        "You don't need alcohol to enjoy yourself.",
        "Social situations get easier.",
        "Plan your weekend sober activities.",
        "Freedom feels different without hangovers."
    )

    private val stopStreakMessages = listOf(
        "Every sober day is a victory.",
        "Your body is healing.",
        "Building a new life, one day at a time.",
        "Sobriety is becoming your new normal.",
        "Proud of your commitment."
    )

    private val stopLongStreakMessages = listOf(
        "Your dedication is inspiring.",
        "Freedom from alcohol feels different each day.",
        "Your body is recovering in ways you can't see.",
        "Sobriety is your superpower.",
        "You're rewriting your story."
    )

    private val stopProgressMessages = listOf(
        "Life without alcohol is unfolding.",
        "Clarity is your new constant.",
        "Every sober day strengthens you.",
        "You're proving it's possible.",
        "Real freedom is what you're building."
    )

    private val stopEncouragementMessages = listOf(
        "One slip doesn't erase your progress.",
        "Relapse is not failure, it's information.",
        "Tomorrow is another chance at sobriety.",
        "Be kind to yourself today.",
        "Many have been where you are. Keep going.",
        "Healing isn't linear."
    )

    private val stopNeutralMessages = listOf(
        "Your sobriety matters.",
        "Stay present, stay sober.",
        "One hour at a time if needed.",
        "You're stronger than you know.",
        "Awareness is your ally."
    )

    // ========== SHARED/FALLBACK MESSAGES ==========

    private val morningMessages = listOf(
        "Every day is a fresh start.",
        "Good morning, new choices await.",
        "Today is full of possibilities.",
        "A mindful morning to you.",
        "Start fresh, stay aware."
    )

    private val eveningMessages = listOf(
        "Be kind to yourself tonight.",
        "You made it through another day.",
        "Rest well, you deserve it.",
        "Evening is for reflection.",
        "Wind down with intention."
    )

    private val weekendMessages = listOf(
        "Weekends test our resolve.",
        "Enjoy your time off mindfully.",
        "Balance rest and awareness.",
        "Relax, but stay present.",
        "You choose your weekend story."
    )

    private val streakMessages = listOf(
        "You're building momentum.",
        "Consistency is paying off.",
        "Keep the streak going.",
        "Your habits are improving.",
        "Steady progress, well done."
    )

    private val longStreakMessages = listOf(
        "Impressive dedication.",
        "Your commitment shows.",
        "Strong habits forming.",
        "You're on a great path.",
        "Real change happening."
    )

    private val progressMessages = listOf(
        "You're making progress.",
        "Small steps, big changes.",
        "Your effort is working.",
        "Moving in the right direction.",
        "Positive trend continues."
    )

    private val encouragementMessages = listOf(
        "Every step forward counts.",
        "Tomorrow is a new chance.",
        "Be kind to yourself today.",
        "Progress isn't always linear.",
        "You're still here, that matters.",
        "One day at a time.",
        "Setbacks are not failures."
    )

    private val neutralMessages = listOf(
        "You're making thoughtful choices.",
        "Awareness is the first step.",
        "Track, reflect, improve.",
        "Stay present, stay aware.",
        "One choice at a time."
    )

    fun getMotivationalMessage(context: MessageContext): String {
        val hour = context.currentTime.hour
        val dayOfWeek = context.currentDate.dayOfWeek
        val isReduce = context.goalMode == GoalMode.REDUCE

        // Priority 1: Long streak (7+ days)
        if (context.isOnStreak && context.streakDays >= 7) {
            return if (isReduce) reduceLongStreakMessages.random() else stopLongStreakMessages.random()
        }

        // Priority 2: Active streak (3-6 days)
        if (context.isOnStreak && context.streakDays >= 3) {
            return if (isReduce) reduceStreakMessages.random() else stopStreakMessages.random()
        }

        // Priority 3: Good progress (reduction > 10% for reduce, any streak for stop)
        if (isReduce && context.isUnderBaseline && context.reductionPercent > 10.0) {
            return reduceProgressMessages.random()
        }
        if (!isReduce && context.isOnStreak && context.streakDays > 0) {
            return stopProgressMessages.random()
        }

        // Priority 4: Needs encouragement (over baseline for reduce, broke streak for stop)
        if (isReduce && !context.isUnderBaseline) {
            return reduceEncouragementMessages.random()
        }
        if (!isReduce && !context.isOnStreak && context.streakDays == 0) {
            return stopEncouragementMessages.random()
        }

        // Priority 5: Time-based messages
        val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

        if (isWeekend) {
            return if (isReduce) reduceWeekendMessages.random() else stopWeekendMessages.random()
        }

        return when {
            hour in 5..11 -> if (isReduce) reduceMorningMessages.random() else stopMorningMessages.random()
            hour in 18..23 || hour in 0..4 -> if (isReduce) reduceEveningMessages.random() else stopEveningMessages.random()
            else -> if (isReduce) reduceNeutralMessages.random() else stopNeutralMessages.random()
        }
    }

    fun getMotivationalMessage(
        currentHour: Int = LocalTime.now().hour,
        isWeekend: Boolean = false,
        isOnStreak: Boolean = false,
        streakDays: Int = 0,
        isUnderBaseline: Boolean = true,
        reductionPercent: Double = 0.0,
        goalMode: GoalMode = GoalMode.REDUCE
    ): String {
        val context = MessageContext(
            currentTime = LocalTime.of(currentHour, 0),
            currentDate = if (isWeekend) {
                LocalDate.now().with(DayOfWeek.SATURDAY)
            } else {
                LocalDate.now().with(DayOfWeek.MONDAY)
            },
            isOnStreak = isOnStreak,
            streakDays = streakDays,
            isUnderBaseline = isUnderBaseline,
            reductionPercent = reductionPercent,
            goalMode = goalMode
        )
        return getMotivationalMessage(context)
    }

    fun parseGoalMode(modeString: String?): GoalMode {
        return when (modeString?.lowercase()) {
            "stop" -> GoalMode.STOP
            else -> GoalMode.REDUCE
        }
    }
}
