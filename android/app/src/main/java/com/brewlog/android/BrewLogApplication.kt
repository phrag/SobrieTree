package com.brewlog.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class BrewLogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}