package com.treetotal.android

import android.app.Activity
import android.view.WindowManager

/**
 * Applies the "prevent screenshots" preference to a window. Crucially it both
 * adds and clears FLAG_SECURE: once the flag is set on a window it stays until
 * explicitly cleared, so simply skipping addFlags() when the pref is off would
 * leave an already-secured window (or a back-stack activity) locked.
 */
object SecureWindow {

    fun apply(activity: Activity) {
        apply(activity, AppPrefs(activity).flagSecure)
    }

    fun apply(activity: Activity, secure: Boolean) {
        if (secure) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
