package com.sobrietree.android

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
                // Bottom-nav tabs should feel like one screen changing, not five
                // activities stacking up. REORDER_TO_FRONT reuses the existing
                // instance instead of building a new one, and killing the
                // animation removes the slide-and-flash between tabs.
                activity.startActivity(
                    Intent(activity, target).addFlags(
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                )
                activity.overridePendingTransition(0, 0)
                // False, not true: returning true would mark the tapped item
                // selected on *this* activity's own bar. Reused activities skip
                // onCreate (and so never re-run wire()), so that stray selection
                // would stick - the next time this tab is reordered back to
                // front it would show the tab you tapped away to, not its own.
                false
            } else {
                false
            }
        }
    }
}
