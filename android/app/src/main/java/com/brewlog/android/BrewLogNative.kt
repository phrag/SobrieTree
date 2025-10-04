package com.brewlog.android

object BrewLogNative {
    init {
        try { System.loadLibrary("brewlog_core") } catch (_: Throwable) {}
    }

    external fun init_brew_log(): String
    external fun init_brew_log_with_path(path: String): String
    external fun add_beer_entry(name: String, alcohol_percentage: Double, volume_ml: Double, notes: String): String
    external fun get_daily_consumption(date: String): Double
    external fun get_weekly_consumption(week_start_date: String): Double
    external fun set_consumption_goal(daily_target: Double, weekly_target: Double, start_date: String, end_date: String): String
    external fun get_beer_entries_json(start_date: String, end_date: String): String
    external fun update_beer_entry_jni(id: String, name: String, alcohol_percentage: Double, volume_ml: Double, notes: String): String
    external fun delete_beer_entry_jni(id: String): String
    external fun update_beer_entry_date_jni(id: String, date: String): String
    external fun add_beer_entry_full_jni(id: String, name: String, alcohol_percentage: Double, volume_ml: Double, date: String, notes: String): String
    external fun delete_all_data(): String
}


