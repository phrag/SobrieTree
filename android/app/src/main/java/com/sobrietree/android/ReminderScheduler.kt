package com.sobrietree.android

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sobrietree.android.engine.DailySchedule
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the opt-in daily check-in notification. WorkManager is used over
 * exact alarms deliberately: a check-in nudge tolerates a few minutes of
 * drift, needs no extra permissions on any API level, and survives reboots.
 *
 * One firing is queued at a time and [ReminderWorker] queues the next when it
 * runs. A 24-hour PeriodicWorkRequest looks like the obvious fit and isn't:
 * only its first run honours the initial delay, after which WorkManager may run
 * the job anywhere inside each 24-hour window, so an evening reminder walks
 * backwards into the morning. A one-shot per day pins the wall-clock time, and
 * recomputing it each firing keeps it correct across daylight saving.
 */
object ReminderScheduler {

    /**
     * Deliberately not the old name: that one holds periodic work from earlier
     * builds, and a unique name can't switch between periodic and one-time.
     * [cancelLegacy] clears it.
     */
    private const val WORK_NAME = "daily_checkin_oneshot"
    private const val LEGACY_WORK_NAME = "daily_checkin"

    fun schedule(context: Context, hour: Int, minute: Int) {
        cancelLegacy(context)
        enqueueNext(context, hour, minute)
    }

    /** Queues tomorrow's firing. Called by the worker once it has notified. */
    fun scheduleNext(context: Context) {
        val prefs = AppPrefs(context)
        if (!prefs.reminderEnabled) return
        enqueueNext(context, prefs.reminderHour, prefs.reminderMinute)
    }

    private fun enqueueNext(context: Context, hour: Int, minute: Int) {
        val delay = DailySchedule.delayMillisUntilNext(LocalDateTime.now(), hour, minute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
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
