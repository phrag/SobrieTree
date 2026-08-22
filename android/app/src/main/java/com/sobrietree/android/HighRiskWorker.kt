package com.sobrietree.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sobrietree.android.engine.HighRiskSupport
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class HighRiskWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val prefs = AppPrefs(context)
        // One firing is queued at a time; line up tomorrow's before doing anything
        // that might return early - see HighRiskScheduler for why.
        if (!prefs.highRiskEnabled) return Result.success()
        HighRiskScheduler.scheduleNext(context)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        createChannel(context)

        // Front-load support in the first days of the journey.
        val start = prefs.baselineSetDate ?: LocalDate.now()
        val daysSinceStart = ChronoUnit.DAYS.between(start, LocalDate.now()).toInt().coerceAtLeast(0)
        val intensity = HighRiskSupport.intensity(daysSinceStart)
        val message = HighRiskSupport.message(intensity, LocalDate.now())

        val tapIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_growth_stage_2)
            .setContentTitle(context.getString(R.string.high_risk_notif_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_growth_stage_2)
                    .setContentTitle(context.getString(R.string.high_risk_notif_title))
                    .build()
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
        return Result.success()
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.high_risk_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.high_risk_channel_description)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "craving_support"
        const val NOTIFICATION_ID = 1002
    }
}
