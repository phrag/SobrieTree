package com.treetotal.android

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Wires the shared bottom navigation bar; replaces the per-activity copies. */
object BottomNavHelper {

    fun wire(activity: Activity, nav: BottomNavigationView, selectedId: Int) {
        nav.menu.clear()
        nav.inflateMenu(R.menu.menu_bottom)
        nav.selectedItemId = selectedId
        nav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedId) return@setOnItemSelectedListener true
            val target = when (item.itemId) {
                R.id.nav_home -> MainActivity::class.java
                R.id.nav_progress -> ProgressActivity::class.java
                R.id.nav_journey -> JourneyActivity::class.java
                R.id.nav_calendar -> CalendarActivity::class.java
                R.id.nav_settings -> SettingsActivity::class.java
                else -> null
            }
            if (target != null) {
                activity.startActivity(Intent(activity, target))
                true
            } else {
                false
            }
        }
    }
}
