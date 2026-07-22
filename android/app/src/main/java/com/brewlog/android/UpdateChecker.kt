package com.brewlog.android

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Opt-in release checker. Talks to the public GitHub Releases API for this repo
 * to find the newest build on the selected channel. Every method here makes a
 * blocking network call and MUST be run off the main thread.
 *
 * This is the only place in the app that touches the network, and it only runs
 * when the user has explicitly enabled updates.
 */
object UpdateChecker {

    private const val TAG = "BrewLogUpdate"
    private const val REPO = "phrag/BrewLog"

    /** A release available for install. [versionName] is a bare "x.y.z". */
    data class Release(
        val versionName: String,
        val apkUrl: String,
        val notes: String,
        val htmlUrl: String
    )

    /** Outcome of a check, so the UI can tell "no release yet" from a real failure. */
    sealed interface Outcome {
        data class Found(val release: Release) : Outcome
        /** The channel has no published release (e.g. no Stable release cut yet). */
        object None : Outcome
        /** Network or parse failure. */
        object Error : Outcome
    }

    const val CHANNEL_STABLE = "stable"
    const val CHANNEL_LATEST = "latest"

    private fun apiUrl(channel: String): String =
        if (channel == CHANNEL_LATEST)
            "https://api.github.com/repos/$REPO/releases/tags/latest"
        else
            "https://api.github.com/repos/$REPO/releases/latest"

    /** Check the newest release on [channel]. */
    fun check(channel: String): Outcome {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(apiUrl(channel)).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BrewLog-Android")
            }
            val code = conn.responseCode
            when {
                // 404 = this channel has no release yet (common for Stable before any release is cut)
                code == 404 -> Outcome.None
                code !in 200..299 -> {
                    Log.w(TAG, "Update check HTTP $code for $channel")
                    Outcome.Error
                }
                else -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    parse(body)?.let { Outcome.Found(it) } ?: Outcome.None
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.javaClass.simpleName}: ${e.message}")
            Outcome.Error
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(body: String): Release? {
        return try {
            val obj = JSONObject(body)
            val assets = obj.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            var versionFromAsset: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (!name.endsWith(".apk")) continue
                val url = a.optString("browser_download_url")
                val v = Regex("""(\d+\.\d+\.\d+)""").find(name)?.groupValues?.get(1)
                if (v != null) {
                    apkUrl = url
                    versionFromAsset = v
                } else if (apkUrl == null) {
                    apkUrl = url // e.g. BrewLog-latest.apk, used only as a fallback
                }
            }
            val resolvedApk = apkUrl ?: return null
            val tagVersion = obj.optString("tag_name").removePrefix("v").removePrefix("V")
            Release(
                versionName = versionFromAsset ?: tagVersion,
                apkUrl = resolvedApk,
                notes = obj.optString("body").trim(),
                htmlUrl = obj.optString("html_url")
            )
        } catch (_: Exception) {
            null
        }
    }

    /** True when [remote] (x.y.z) is a strictly higher version than [current]. */
    fun isNewer(remote: String, current: String): Boolean {
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.trim().split(".").map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
