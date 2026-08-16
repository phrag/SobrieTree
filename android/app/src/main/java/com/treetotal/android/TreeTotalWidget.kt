package com.treetotal.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/**
 * Home-screen widget: today's ring, and one tap to log the favourite drink.
 *
 * Logging without opening the app means logging without a confirmation step, so
 * every tap is followed by a 60-second undo shown in the widget itself. That
 * keeps the fast path fast while making a mis-tap costless - the same reason the
 * drinks manager stopped logging on row tap.
 *
 * Nothing here touches the network; the widget reads and writes the same local
 * database as the app.
 */
class TreeTotalWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_LOG_FAVORITE = "com.treetotal.android.widget.LOG_FAVORITE"
        const val ACTION_UNDO = "com.treetotal.android.widget.UNDO"

        /** How long the undo stays offered after a widget log. */
        const val UNDO_WINDOW_MS = 60_000L

        /** Redraws every placed widget. Call after anything that changes today's total. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TreeTotalWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) render(context, manager, id)
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = AppPrefs(context)
            val gamification = GamificationManager(context)
            val state = gamification.homeState()
            val views = RemoteViews(context.packageName, R.layout.widget_treetotal)

            val remaining = (1.0 - state.metrics.dailyRatio).coerceIn(0.0, 1.0).toFloat()
            views.setImageViewBitmap(
                R.id.widget_ring,
                WidgetRing.render(context, remaining, state.isTodayAf)
            )

            val units = gamification.unitsState()
            views.setTextViewText(
                R.id.widget_status,
                if (state.isTodayAf) context.getString(R.string.widget_af_today)
                else context.getString(R.string.widget_logged_today, formatUnits(units.unitsToday))
            )

            val favorite = DrinkPresetStore.getPresets(prefs.prefs)
                .let { presets -> presets.firstOrNull { it.favorite } ?: presets.firstOrNull() }

            val undoLive = prefs.lastWidgetEntryId != null &&
                System.currentTimeMillis() - prefs.lastWidgetEntryAt < UNDO_WINDOW_MS

            when {
                undoLive -> {
                    views.setViewVisibility(R.id.widget_action, View.VISIBLE)
                    views.setTextViewText(R.id.widget_action, context.getString(R.string.widget_undo))
                    views.setOnClickPendingIntent(R.id.widget_action, broadcast(context, ACTION_UNDO))
                }
                favorite != null -> {
                    views.setViewVisibility(R.id.widget_action, View.VISIBLE)
                    views.setTextViewText(R.id.widget_action, context.getString(R.string.widget_log, favorite.name))
                    views.setOnClickPendingIntent(R.id.widget_action, broadcast(context, ACTION_LOG_FAVORITE))
                }
                else -> {
                    // No saved drinks yet: the whole widget just opens the app.
                    views.setViewVisibility(R.id.widget_action, View.GONE)
                }
            }

            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            manager.updateAppWidget(widgetId, views)
        }

        private fun formatUnits(units: Double): String =
            if (units == units.toInt().toDouble()) units.toInt().toString()
            else String.format("%.1f", units)

        private fun broadcast(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                Intent(context, TreeTotalWidget::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        for (id in widgetIds) render(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_LOG_FAVORITE -> logFavorite(context)
            ACTION_UNDO -> undo(context)
            else -> return
        }
        refresh(context)
    }

    private fun logFavorite(context: Context) {
        val prefs = AppPrefs(context)
        val presets = DrinkPresetStore.getPresets(prefs.prefs)
        val favorite = presets.firstOrNull { it.favorite } ?: presets.firstOrNull() ?: return
        val repo = EntryRepository()
        val today = GamificationManager(context).todayEffective()
        val id = repo.addEntryAtReturningId(
            today,
            favorite.name,
            favorite.abv,
            favorite.volume.toDouble(),
            ""
        ) ?: return
        prefs.lastWidgetEntryId = id
        prefs.lastWidgetEntryAt = System.currentTimeMillis()
    }

    private fun undo(context: Context) {
        val prefs = AppPrefs(context)
        val id = prefs.lastWidgetEntryId ?: return
        EntryRepository().deleteEntry(id)
        prefs.lastWidgetEntryId = null
        prefs.lastWidgetEntryAt = 0L
    }
}
