package com.brewlog.android

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Handles the "Still alcohol-free" action on the daily check-in notification.
 * Tapping it dismisses the reminder and replaces it with a small celebration —
 * a low-effort way to affirm a dry day and get a bit of positive reinforcement.
 */
class CheckinActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AFFIRM_AF) return

        val manager = NotificationManagerCompat.from(context)
        manager.cancel(ReminderWorker.NOTIFICATION_ID)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val streak = try {
            GamificationManager(context).homeState().streaks.displayStreak
        } catch (_: Exception) { 0 }
        val body = if (streak >= 2) {
            context.getString(R.string.affirm_celebration_body, streak)
        } else {
            context.getString(R.string.affirm_celebration_body_generic)
        }

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_journey)
            .setContentTitle(context.getString(R.string.affirm_celebration_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        try {
            manager.notify(CELEBRATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the notify call; nothing to do
        }
    }

    companion object {
        const val ACTION_AFFIRM_AF = "com.brewlog.android.ACTION_AFFIRM_AF"
        const val CELEBRATION_ID = 1002
    }
}
