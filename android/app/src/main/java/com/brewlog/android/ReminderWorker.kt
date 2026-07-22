package com.brewlog.android

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

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val prefs = AppPrefs(context)
        if (!prefs.reminderEnabled) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        createChannel(context)

        val home = try { GamificationManager(context).homeState() } catch (_: Exception) { null }
        val message = buildMessage(context, home?.streaks?.displayStreak ?: 0)
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_journey)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            // Generic content only on the lock screen - this is a private app
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_nav_journey)
                    .setContentTitle(context.getString(R.string.reminder_title))
                    .build()
            )

        // When the day is still alcohol-free, offer a one-tap affirmation that
        // celebrates it — no need to open the app.
        if (home?.isTodayAf == true) {
            val affirmIntent = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, CheckinActionReceiver::class.java)
                    .setAction(CheckinActionReceiver.ACTION_AFFIRM_AF),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, context.getString(R.string.reminder_affirm_action), affirmIntent)
        }

        val notification = builder.build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the notify call; nothing to do
        }
        return Result.success()
    }

    private fun buildMessage(context: Context, streak: Int): String {
        return if (streak >= 2) {
            context.getString(R.string.reminder_streak_message, streak)
        } else {
            val pool = context.resources.getStringArray(R.array.reminder_messages)
            pool[(System.currentTimeMillis() / 86_400_000L % pool.size).toInt()]
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "checkin"
        const val NOTIFICATION_ID = 1001
    }
}
