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
            "h_3", 3, "Day 3 - Deeper sleep begins",
            "Alcohol suppresses REM sleep. A few clear nights in, sleep cycles start to normalise and mornings feel less foggy."
        ),
        HealthMilestone(
            "h_7", 7, "1 week - Energy returns",
            "Better-quality sleep compounds: steadier energy, better hydration and sharper focus through the day."
        ),
        HealthMilestone(
            "h_14", 14, "2 weeks - Skin and digestion",
            "Skin looks fresher as inflammation and dehydration ease. Stomach acid production normalises and digestion improves."
        ),
        HealthMilestone(
            "h_30", 30, "1 month - Measurable health shifts",
            "Studies of month-long breaks show blood pressure down around 6%, insulin resistance down about 25%, and liver fat falling."
        ),
        HealthMilestone(
            "h_60", 60, "2 months - Mood steadies",
            "With the rebound anxiety of regular drinking gone, mood and baseline anxiety levels typically improve noticeably."
        ),
        HealthMilestone(
            "h_90", 90, "3 months - The habit rewires",
            "Around this point new routines stop feeling like effort. Cravings weaken as reward pathways adapt to life with less alcohol."
        ),
        HealthMilestone(
            "h_180", 180, "6 months - Liver recovery",
            "For moderate drinkers, liver function markers can return to a healthy range as the organ repairs itself."
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
}
