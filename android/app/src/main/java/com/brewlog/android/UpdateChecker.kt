package com.brewlog.android

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Opt-in release checker. Talks to the public GitHub Releases API for this repo
 * to find the newest build on the selected channel. Every method here makes a
 * blocking network call and MUST be run off the main thread; all failures are
 * swallowed and surface as null so a check can never crash or block the app.
 *
 * This is the only place in the app that touches the network, and it only runs
 * when the user has explicitly enabled updates.
 */
object UpdateChecker {

    private const val REPO = "phrag/BrewLog"

    /** A release available for install. [versionName] is a bare "x.y.z". */
    data class Release(
        val versionName: String,
        val apkUrl: String,
        val notes: String,
        val htmlUrl: String
    )

    const val CHANNEL_STABLE = "stable"
    const val CHANNEL_LATEST = "latest"

    private fun apiUrl(channel: String): String =
        if (channel == CHANNEL_LATEST)
            "https://api.github.com/repos/$REPO/releases/tags/latest"
        else
            "https://api.github.com/repos/$REPO/releases/latest"

    /** Fetch the newest release on [channel], or null on any error / no release. */
    fun fetchLatest(channel: String): Release? {
        return try {
            val body = httpGet(apiUrl(channel)) ?: return null
            val obj = JSONObject(body)
            val assets = obj.optJSONArray("assets")
            var apkUrl: String? = null
            var versionFromAsset: String? = null
            if (assets != null) {
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
            }
            val resolvedApk = apkUrl ?: return null
            val tagVersion = obj.optString("tag_name").removePrefix("v").removePrefix("V")
            val version = versionFromAsset ?: tagVersion
            Release(
                versionName = version,
                apkUrl = resolvedApk,
                notes = obj.optString("body").trim(),
                htmlUrl = obj.optString("html_url")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BrewLog-Android")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
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
