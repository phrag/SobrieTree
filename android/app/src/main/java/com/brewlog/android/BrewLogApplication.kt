package com.brewlog.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class BrewLogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        try {
            System.loadLibrary("brewlog_core")
        } catch (_: Throwable) {}

        // Initialize native database at app startup
        try {
            val dbPath = this.getDatabasePath("brewlog.db").absolutePath
            val result = BrewLogNative.init_brew_log_with_path(dbPath)
            if (!result.startsWith("OK")) {
                android.util.Log.e("BrewLog", "Native init failed: $result")
            }
        } catch (t: Throwable) {
            android.util.Log.e("BrewLog", "Failed to init native DB", t)
        }
    }
}