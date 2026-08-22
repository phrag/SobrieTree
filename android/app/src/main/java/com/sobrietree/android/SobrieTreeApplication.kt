package com.sobrietree.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class SobrieTreeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = AppPrefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        Money.applyFrom(prefs)
        try {
            System.loadLibrary("sobrietree_core")
        } catch (_: Throwable) {}

        // Initialize native database at app startup
        try {
            val dbPath = this.getDatabasePath("sobrietree.db").absolutePath
            val result = SobrieTreeNative.init_brew_log_with_path(dbPath)
            if (!result.startsWith("OK")) {
                android.util.Log.e("SobrieTree", "Native init failed: $result")
            }
        } catch (t: Throwable) {
            android.util.Log.e("SobrieTree", "Failed to init native DB", t)
        }
    }
}
