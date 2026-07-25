package com.treetotal.android

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules a daily supportive nudge shortly before the user's usual
 * start-drinking time, so encouragement lands just before the craving does.
 * Local-only, WorkManager, same rationale as [ReminderScheduler].
 */
object HighRiskScheduler {

    private const val WORK_NAME = "high_risk_support"
    /** Fire this many minutes before the stated time so support arrives first. */
    const val LEAD_MINUTES = 30L

    fun schedule(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var fire = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            .minusMinutes(LEAD_MINUTES)
        if (!fire.isAfter(now)) fire = fire.plusDays(1)
        val initialDelayMinutes = Duration.between(now, fire).toMinutes()

        val request = PeriodicWorkRequestBuilder<HighRiskWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
