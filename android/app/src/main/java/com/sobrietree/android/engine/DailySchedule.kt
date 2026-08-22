package com.sobrietree.android.engine

import java.time.Duration
import java.time.LocalDateTime

/**
 * When the next daily notification should fire.
 *
 * This exists because the schedulers used to hand a 24-hour PeriodicWorkRequest
 * an initial delay and assume that pinned the time of day. It doesn't: only the
 * first run honours the delay, and WorkManager is then free to run the job
 * anywhere inside each following 24-hour window. An evening reminder drifts
 * earlier every day until it arrives in the morning.
 *
 * The fix is a one-shot per firing, which needs the next time recomputing after
 * every run - so the arithmetic lives here, where it can be tested, rather than
 * inside a Worker.
 */
object DailySchedule {

    /**
     * Milliseconds from [now] until the next occurrence of [hour]:[minute],
     * brought forward by [leadMinutes]. Always strictly in the future: a time
     * that has already passed today rolls to tomorrow.
     *
     * Recomputing against local time each firing is also what keeps the
     * reminder at the same wall-clock time across a daylight-saving change.
     */
    fun delayMillisUntilNext(
        now: LocalDateTime,
        hour: Int,
        minute: Int,
        leadMinutes: Long = 0L
    ): Long {
        var fire = now.toLocalDate()
            .atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            .minusMinutes(leadMinutes)
        if (!fire.isAfter(now)) fire = fire.plusDays(1)
        return Duration.between(now, fire).toMillis()
    }
}
