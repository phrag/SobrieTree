package com.sobrietree.android.engine

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Which logging day a moment belongs to, given where the user puts the
 * boundary between one day and the next.
 *
 * The setting exists because drinking doesn't respect midnight: a drink at 1am
 * belongs to the night before. So an early boundary extends yesterday forward.
 *
 * The catch is that people set this from either end. Someone who drinks late
 * means "my day runs until 3am". Someone who sets 23 means the opposite: "past
 * 11pm is tomorrow's problem". Read the second the same way as the first and
 * almost the whole calendar day gets labelled as yesterday - which is what
 * happened: at 10am with the boundary at 23, the app called it yesterday and
 * showed yesterday's drinks under "Today".
 *
 * So the boundary is read in whichever direction it can sensibly mean. Early
 * hours pull the previous day forward; late hours push the current one on.
 * Both land on the calendar date the logging day mostly covers.
 */
object DayBoundary {

    /** At or below this, the boundary reads as "yesterday runs on until here". */
    const val LATE_NIGHT_LIMIT = 12

    fun effectiveDate(now: LocalDateTime, endOfDayHour: Int): LocalDate {
        val hour = endOfDayHour.coerceIn(0, 23)
        return when {
            // Early boundary: before it, the previous day is still running.
            hour <= LATE_NIGHT_LIMIT ->
                if (now.hour < hour) now.toLocalDate().minusDays(1) else now.toLocalDate()
            // Late boundary: at or after it, the next day has already started.
            else ->
                if (now.hour >= hour) now.toLocalDate().plusDays(1) else now.toLocalDate()
        }
    }
}
