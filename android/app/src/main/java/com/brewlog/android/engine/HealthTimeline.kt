package com.brewlog.android.engine

import java.time.LocalDate

data class HealthMilestone(
    val id: String,
    val afDays: Int,
    val title: String,
    val description: String
)

/**
 * The recovery arc shown on the Journey tab, keyed to the user's actual
 * cumulative alcohol-free days. Copy is deliberately concrete: what the
 * body is doing, not vague cheerleading.
 */
object HealthTimeline {

    val milestones: List<HealthMilestone> = listOf(
        HealthMilestone(
            "h_1", 1, "Day 1 - Rehydration",
            "Within 24 alcohol-free hours your body rehydrates, blood sugar steadies and your stomach lining starts to settle."
        ),
        HealthMilestone(
            "h_2", 2, "48 hours - Clearing out",
            "The alcohol is fully out of your system and your body is flushing the by-products. For heavier drinkers this is when acute symptoms peak, then ease."
        ),
        HealthMilestone(
            "h_3", 3, "Day 3 - Deeper sleep begins",
            "Alcohol suppresses REM sleep. A few clear nights in, sleep cycles start to normalise and mornings feel less foggy."
        ),
        HealthMilestone(
            "h_5", 5, "Day 5 - Sleep rebounds",
            "REM sleep is bouncing back - dreams return and you start waking genuinely rested."
        ),
        HealthMilestone(
            "h_7", 7, "1 week - Energy returns",
            "Better-quality sleep compounds: steadier energy, better hydration and sharper focus through the day."
        ),
        HealthMilestone(
            "h_10", 10, "10 days - Skin & focus",
            "Reduced inflammation and better hydration begin to show in your skin, and daytime concentration keeps sharpening."
        ),
        HealthMilestone(
            "h_14", 14, "2 weeks - Skin, digestion, weight",
            "Skin looks fresher as inflammation and dehydration ease, stomach acid normalises, and cutting alcohol calories can start showing on the scale."
        ),
        HealthMilestone(
            "h_21", 21, "3 weeks - Blood pressure eases",
            "Blood pressure often drifts back toward a healthier range around now - one of the biggest silent risk factors quietly easing."
        ),
        HealthMilestone(
            "h_30", 30, "1 month - Measurable health shifts",
            "Studies of month-long breaks show blood pressure down around 6%, insulin resistance down about 25%, and liver fat falling."
        ),
        HealthMilestone(
            "h_45", 45, "6 weeks - Immune boost",
            "Alcohol suppresses immune defences; a few weeks off and your body fights off infections more effectively."
        ),
        HealthMilestone(
            "h_60", 60, "2 months - Mood steadies",
            "With the rebound anxiety of regular drinking gone, mood and baseline anxiety levels typically improve noticeably."
        ),
        HealthMilestone(
            "h_90", 90, "3 months - The habit rewires",
            "New routines stop feeling like effort and cravings weaken as reward pathways adapt. Cancer-related growth factors also fall, lowering long-term risk."
        ),
        HealthMilestone(
            "h_120", 120, "4 months - Sharper mind",
            "Concentration, memory and mental clarity keep improving as the brain recovers from alcohol's effects."
        ),
        HealthMilestone(
            "h_180", 180, "6 months - Liver recovery",
            "For moderate drinkers, liver function markers can return to a healthy range as the organ repairs itself, and long-term conditions keep improving."
        ),
        HealthMilestone(
            "h_270", 270, "9 months - Firing on all cylinders",
            "Energy, sleep and skin are typically at their best, and the daily mental load of managing drinking is long behind you."
        ),
        HealthMilestone(
            "h_365", 365, "1 year - Lasting protection",
            "A full year meaningfully lowers your risk of liver disease, high blood pressure, heart disease and several cancers - and the money and health savings really add up."
        )
    )

    /** The calendar date on which the Nth cumulative AF day was completed, if reached. */
    fun dateReached(ledger: DayLedger, afDaysThreshold: Int): LocalDate? {
        var count = 0
        for (day in ledger.completedDays) {
            if (ledger.isCompletedAfDay(day)) {
                count++
                if (count >= afDaysThreshold) return day
            }
        }
        return null
    }

    fun next(totalAfDays: Int): HealthMilestone? = milestones.firstOrNull { it.afDays > totalAfDays }

    /** The most recent recovery stage whose AF-day threshold has been reached, if any. */
    fun current(totalAfDays: Int): HealthMilestone? = milestones.lastOrNull { it.afDays <= totalAfDays }
}
