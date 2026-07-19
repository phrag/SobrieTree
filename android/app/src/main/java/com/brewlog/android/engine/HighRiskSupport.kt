package com.brewlog.android.engine

import java.time.LocalDate

/**
 * Support around the time of day the user usually starts drinking - the
 * highest-risk window for a lapse. Support is strongest in the first days of
 * the journey (when cravings are sharpest) and eases as new habits settle.
 *
 * Pure logic (no android imports): times are minutes-of-day so it is testable.
 */
object HighRiskSupport {

    enum class Intensity { INTENSIVE, STEADY, LIGHT }

    /** How much hands-on support to give, based on days since the journey began. */
    fun intensity(daysSinceStart: Int): Intensity = when {
        daysSinceStart <= 7 -> Intensity.INTENSIVE
        daysSinceStart <= 21 -> Intensity.STEADY
        else -> Intensity.LIGHT
    }

    /**
     * Is [nowMinutes] inside the risk window [start - before, start + after]?
     * Handles wrap-around midnight. All values are minutes-of-day in [0, 1440).
     */
    fun isInWindow(nowMinutes: Int, startMinutes: Int, beforeMin: Int = 30, afterMin: Int = 120): Boolean {
        fun norm(m: Int) = ((m % 1440) + 1440) % 1440
        val from = norm(startMinutes - beforeMin)
        val to = norm(startMinutes + afterMin)
        val now = norm(nowMinutes)
        return if (from <= to) now in from..to else now >= from || now <= to
    }

    val pools: Map<Intensity, List<String>> = mapOf(
        Intensity.INTENSIVE to listOf(
            "This is usually when the pull is strongest. It peaks and passes in about 20 minutes — set a timer and ride it out.",
            "Craving o'clock. Pour something cold and non-alcoholic first; the urge fades while your hands are busy.",
            "The hardest part of the day is right now, and it's temporary. Step outside for five minutes — the wave breaks.",
            "Your body is expecting a drink out of habit, not need. Name the craving, breathe, and let it roll past.",
            "These early evenings are the whole battle. Get through this one hour and tomorrow's is easier.",
            "Right now is the test. A glass of water, a snack, a walk — any of them outlasts the urge."
        ),
        Intensity.STEADY to listOf(
            "Your usual time. You've navigated this before — what will you reach for instead tonight?",
            "This is your window. Have a plan ready: a drink you like that isn't alcohol, or something to do with your hands.",
            "The habit still nudges around now, but it's quieter than week one. Answer it with your alternative.",
            "You're rewiring this time of day. Each evening you skip makes the next one lighter.",
            "Craving window open. You know the 20-minute rule — start the clock and it works.",
            "This is where your goal is won or lost. You've been winning it. Keep going."
        ),
        Intensity.LIGHT to listOf(
            "Evening check-in — you've reshaped this time of day. Keep it easy.",
            "Your old drinking hour. Look how far it's come. Enjoy the calm.",
            "This used to be a craving window. Now it's just an evening. Nice work.",
            "A gentle nudge at your usual time — you've got this well in hand.",
            "The urge barely shows up here anymore. That's months of practice paying off.",
            "Your reshaped evening. Water within reach, and carry on."
        )
    )

    fun message(intensity: Intensity, date: LocalDate): String {
        val pool = pools.getValue(intensity)
        val index = ((date.toEpochDay() % pool.size) + pool.size).toInt() % pool.size
        return pool[index]
    }
}
