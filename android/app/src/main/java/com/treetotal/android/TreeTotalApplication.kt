package com.treetotal.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class TreeTotalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = AppPrefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        Money.applyFrom(prefs)
        try {
            System.loadLibrary("treetotal_core")
        } catch (_: Throwable) {}

        // Initialize native database at app startup
        try {
            val dbPath = this.getDatabasePath("treetotal.db").absolutePath
            val result = TreeTotalNative.init_brew_log_with_path(dbPath)
            if (!result.startsWith("OK")) {
                android.util.Log.e("TreeTotal", "Native init failed: $result")
            }
        } catch (t: Throwable) {
            android.util.Log.e("TreeTotal", "Failed to init native DB", t)
        }
    }
}
