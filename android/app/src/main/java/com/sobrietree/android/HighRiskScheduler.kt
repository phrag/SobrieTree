package com.sobrietree.android

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sobrietree.android.engine.DailySchedule
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules a daily supportive nudge shortly before the user's usual
 * start-drinking time, so encouragement lands just before the craving does.
 * Local-only, WorkManager, same rationale as [ReminderScheduler] - including
 * the one-shot-per-day pattern, since a drifting nudge is worse here than for
 * the check-in: arriving at breakfast instead of before the evening it was set
 * for makes it noise rather than support.
 */
object HighRiskScheduler {

    private const val WORK_NAME = "high_risk_support_oneshot"
    private const val LEGACY_WORK_NAME = "high_risk_support"

    /** Fire this many minutes before the stated time so support arrives first. */
    const val LEAD_MINUTES = 30L

    fun schedule(context: Context, hour: Int, minute: Int) {
        cancelLegacy(context)
        enqueueNext(context, hour, minute)
    }

    /** Queues tomorrow's nudge. Called by the worker once it has notified. */
    fun scheduleNext(context: Context) {
        val prefs = AppPrefs(context)
        if (!prefs.highRiskEnabled) return
        enqueueNext(context, prefs.highRiskHour, prefs.highRiskMinute)
    }

    private fun enqueueNext(context: Context, hour: Int, minute: Int) {
        val delay = DailySchedule.delayMillisUntilNext(LocalDateTime.now(), hour, minute, LEAD_MINUTES)
        val request = OneTimeWorkRequestBuilder<HighRiskWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        cancelLegacy(context)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun cancelLegacy(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
    }
}
