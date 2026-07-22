package com.brewlog.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK to private cache storage and hands it to the system
 * package installer. The user still confirms the install in the OS dialog — the
 * app never installs silently. Only used when the user has opted into updates
 * and tapped "Update".
 */
object UpdateInstaller {

    /** Download [url] to a private cache file. Blocking — run off the main thread. Null on failure. */
    fun downloadApk(context: Context, url: String): File? {
        var conn: HttpURLConnection? = null
        return try {
            val out = File(context.cacheDir, "update.apk")
            if (out.exists()) out.delete()
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BrewLog-Android")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                out.outputStream.use { output -> input.copyTo(output) }
            }
            out
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Launch the system installer for a downloaded APK via a FileProvider uri. */
    fun installApk(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.updateprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
