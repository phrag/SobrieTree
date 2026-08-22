package com.sobrietree.android

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONObject
import java.time.LocalDate

/**
 * Typed access to the single SharedPreferences file used across the app.
 * All keys live here so activities stop re-declaring "sobrietree_prefs" strings.
 */
class AppPrefs(context: Context) {

    val prefs: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit().putBoolean("onboarding_complete", value).apply()

    var defaultDrinkSizeMl: Int
        get() = prefs.getInt("default_beer_size", 500)
        set(value) = prefs.edit().putInt("default_beer_size", value).apply()

    var defaultDrinkStrength: Float
        get() = prefs.getFloat("default_beer_strength", 5.0f)
        set(value) = prefs.edit().putFloat("default_beer_strength", value).apply()

    var endOfDayHour: Int
        get() = prefs.getInt("end_of_day_hour", 3)
        set(value) = prefs.edit().putInt("end_of_day_hour", value).apply()

    var startOfWeek: Int
        get() = prefs.getInt("start_of_week", 1)
        set(value) = prefs.edit().putInt("start_of_week", value).apply()

    var flagSecure: Boolean
        get() = prefs.getBoolean("flag_secure", true)
        set(value) = prefs.edit().putBoolean("flag_secure", value).apply()

    var goalDailyMl: Double
        get() = prefs.getFloat("goal_daily_ml", 0f).toDouble()
        set(value) = prefs.edit().putFloat("goal_daily_ml", value.toFloat()).apply()

    var goalWeeklyMl: Double
        get() = prefs.getFloat("goal_weekly_ml", 0f).toDouble()
        set(value) = prefs.edit().putFloat("goal_weekly_ml", value.toFloat()).apply()

    var baselineDailyMl: Double
        get() = prefs.getFloat("baseline_daily_ml", 0f).toDouble()
        set(value) = prefs.edit().putFloat("baseline_daily_ml", value.toFloat()).apply()

    var baselineSetDate: LocalDate?
        get() = prefs.getString("baseline_set_date", null)?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }
        set(value) = prefs.edit().putString("baseline_set_date", value?.toString()).apply()

    /** One of AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM / MODE_NIGHT_NO / MODE_NIGHT_YES. */
    var themeMode: Int
        get() = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt("theme_mode", value).apply()

    /** Why the user wants to cut back, chosen during onboarding (e.g. "sleep", "money"). */
    var motivations: Set<String>
        get() = prefs.getStringSet("motivation", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("motivation", value).apply()

    /** What the user used to spend on alcohol per week, in their currency. Powers money saved. */
    var baselineWeeklySpend: Float
        get() = prefs.getFloat("baseline_weekly_spend", 0f)
        set(value) = prefs.edit().putFloat("baseline_weekly_spend", value).apply()

    /** ISO 4217 currency code (e.g. "EUR"); null = follow the device locale. */
    var currencyCode: String?
        get() = prefs.getString("currency_code", null)
        set(value) = prefs.edit().putString("currency_code", value).apply()

    var reminderEnabled: Boolean
        get() = prefs.getBoolean("reminder_enabled", false)
        set(value) = prefs.edit().putBoolean("reminder_enabled", value).apply()

    var reminderHour: Int
        get() = prefs.getInt("reminder_hour", 20)
        set(value) = prefs.edit().putInt("reminder_hour", value).apply()

    var reminderMinute: Int
        get() = prefs.getInt("reminder_minute", 0)
        set(value) = prefs.edit().putInt("reminder_minute", value).apply()

    /** Support around the user's usual start-drinking time. */
    var highRiskEnabled: Boolean
        get() = prefs.getBoolean("high_risk_enabled", false)
        set(value) = prefs.edit().putBoolean("high_risk_enabled", value).apply()

    var highRiskHour: Int
        get() = prefs.getInt("high_risk_hour", 18)
        set(value) = prefs.edit().putInt("high_risk_hour", value).apply()

    var highRiskMinute: Int
        get() = prefs.getInt("high_risk_minute", 0)
        set(value) = prefs.edit().putInt("high_risk_minute", value).apply()

    /** Badge id -> ISO date earned. */
    var badgesEarned: Map<String, String>
        get() {
            val json = prefs.getString("badges_earned", "{}") ?: "{}"
            return try {
                val obj = JSONObject(json)
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } catch (_: Exception) { emptyMap() }
        }
        set(value) {
            val obj = JSONObject()
            value.forEach { (k, v) -> obj.put(k, v) }
            prefs.edit().putString("badges_earned", obj.toString()).apply()
        }

    /** ISO dates of lapse days already bridged by a streak shield (idempotent across recomputes). */
    var shieldBridgedDates: Set<String>
        get() = prefs.getStringSet("shield_bridged_dates", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("shield_bridged_dates", value).apply()

    /** When the last encrypted backup was written, so Settings can say how stale it is. */
    var lastBackupAt: Long
        get() = prefs.getLong("last_backup_at", 0L)
        set(value) = prefs.edit().putLong("last_backup_at", value).apply()

    /**
     * The entry the widget logged most recently, and when. The widget logs in a
     * single tap with no confirmation, so it offers an undo for a short window
     * afterwards; this is the only state that needs.
     */
    var lastWidgetEntryId: String?
        get() = prefs.getString("widget_last_entry_id", null)
        set(value) = prefs.edit().putString("widget_last_entry_id", value).apply()

    var lastWidgetEntryAt: Long
        get() = prefs.getLong("widget_last_entry_at", 0L)
        set(value) = prefs.edit().putLong("widget_last_entry_at", value).apply()

    /** Milestone ids that already had their celebration sheet shown. */
    var celebratedMilestones: Set<String>
        get() = prefs.getStringSet("milestones_celebrated", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("milestones_celebrated", value).apply()

    /**
     * Opt-in over-the-air update checks. Off by default — this is the only
     * feature that ever uses the network, so the app stays fully offline
     * unless the user turns this on.
     */
    var updatesEnabled: Boolean
        get() = prefs.getBoolean("updates_enabled", false)
        set(value) = prefs.edit().putBoolean("updates_enabled", value).apply()

    /** "stable" = full releases only; "latest" = rolling pre-release builds. */
    var updateChannel: String
        get() = prefs.getString("update_channel", "latest") ?: "latest"
        set(value) = prefs.edit().putString("update_channel", value).apply()

    /** Epoch millis of the last update check, used to throttle the on-open check. */
    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0L)
        set(value) = prefs.edit().putLong("last_update_check", value).apply()

    companion object {
        const val NAME = "sobrietree_prefs"
    }
}
