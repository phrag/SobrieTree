package com.sobrietree.android

import android.content.Context
import android.net.Uri
import com.sobrietree.android.engine.BackupCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Whole-app backup: entries, saved drinks and the settings that give them
 * meaning, encrypted with the user's passphrase before anything touches disk.
 *
 * The app holds no network permission, so a backup is the only way data can
 * leave the device - and the only way it survives the device being lost. The
 * file is written wherever the user points it (SD card, a folder their own sync
 * client watches); SobrieTree never sends it anywhere itself.
 *
 * Restore is additive and idempotent: entries already present by id are left
 * alone, so restoring twice, or onto a phone that has been used since, doesn't
 * duplicate a drinking history or silently overwrite one.
 */
object BackupManager {

    private const val PAYLOAD_VERSION = 1

    /** Years of history a backup reaches back for. Nothing predates the app itself. */
    private const val LOOKBACK_YEARS = 20L

    data class RestoreResult(
        val entriesRestored: Int,
        val entriesAlreadyPresent: Int,
        val presetsRestored: Int,
        val settingsRestored: Boolean
    )

    fun backupFileName(today: LocalDate): String = "sobrietree-backup-$today.ttbk"

    // ---------------------------------------------------------------- write

    /** Serialises everything worth keeping into the payload that gets encrypted. */
    fun serialize(context: Context): ByteArray {
        val prefs = AppPrefs(context)
        val repo = EntryRepository()
        val today = LocalDate.now()
        val entries = repo.getEntries(today.minusYears(LOOKBACK_YEARS), today.plusDays(1))

        val entriesJson = JSONArray()
        for (e in entries) {
            entriesJson.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("alcohol_percentage", e.alcoholPercentage)
                    put("volume_ml", e.volumeMl)
                    put("date", e.date)
                    put("notes", e.notes)
                }
            )
        }

        val presetsJson = JSONArray()
        for (p in DrinkPresetStore.getPresets(prefs.prefs)) presetsJson.put(p.toJson())

        val settings = JSONObject().apply {
            put("baseline_daily_ml", prefs.baselineDailyMl)
            put("goal_daily_ml", prefs.goalDailyMl)
            put("goal_weekly_ml", prefs.goalWeeklyMl)
            put("baseline_weekly_spend", prefs.baselineWeeklySpend.toDouble())
            put("currency_code", prefs.currencyCode ?: "")
            put("baseline_set_date", prefs.baselineSetDate?.toString() ?: "")
        }

        val payload = JSONObject().apply {
            put("payload_version", PAYLOAD_VERSION)
            put("written_at", java.time.Instant.now().toString())
            put("entries", entriesJson)
            put("presets", presetsJson)
            put("settings", settings)
        }
        return payload.toString().toByteArray(Charsets.UTF_8)
    }

    /** Encrypts and writes a backup to [uri]. Throws on I/O or crypto failure. */
    fun writeTo(context: Context, uri: Uri, passphrase: CharArray) {
        val blob = BackupCrypto.encrypt(serialize(context), passphrase)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(blob) }
            ?: throw IllegalStateException("Couldn't open that location for writing.")
        AppPrefs(context).lastBackupAt = System.currentTimeMillis()
    }

    // ----------------------------------------------------------------- read

    /**
     * Decrypts [uri] and merges it in. Throws [BackupCrypto.BackupFormatException]
     * for a wrong passphrase or a file that isn't ours - both recoverable by the
     * user, so callers should show the message rather than a generic failure.
     */
    fun restoreFrom(context: Context, uri: Uri, passphrase: CharArray): RestoreResult {
        val blob = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Couldn't read that file.")
        val json = JSONObject(String(BackupCrypto.decrypt(blob, passphrase), Charsets.UTF_8))

        val prefs = AppPrefs(context)
        val repo = EntryRepository()

        val entriesJson = json.optJSONArray("entries") ?: JSONArray()
        // One read of what's already here, so restoring is O(n) rather than a
        // query per entry - a long history makes that difference visible.
        val today = LocalDate.now()
        val existingIds = repo.getEntries(today.minusYears(LOOKBACK_YEARS), today.plusDays(1))
            .map { it.id }
            .toHashSet()

        var restored = 0
        var alreadyPresent = 0
        for (i in 0 until entriesJson.length()) {
            val e = entriesJson.optJSONObject(i) ?: continue
            val id = e.optString("id").takeIf { it.isNotBlank() } ?: continue
            if (id in existingIds) {
                alreadyPresent++
                continue
            }
            val date = try {
                LocalDate.parse(e.optString("date"))
            } catch (_: Exception) {
                continue
            }
            val ok = repo.addEntryAt(
                date,
                e.optString("name", "Drink"),
                e.optDouble("alcohol_percentage", 0.0),
                e.optDouble("volume_ml", 0.0),
                e.optString("notes", "")
            )
            if (ok) {
                restored++
                existingIds.add(id)
            }
        }

        val presetsJson = json.optJSONArray("presets") ?: JSONArray()
        var presetsRestored = 0
        if (presetsJson.length() > 0) {
            val existing = DrinkPresetStore.getPresets(prefs.prefs)
            val known = existing.map { it.name.lowercase() to it.volume }.toHashSet()
            val additions = mutableListOf<DrinkPreset>()
            for (i in 0 until presetsJson.length()) {
                val p = try {
                    DrinkPreset.fromJson(presetsJson.getJSONObject(i))
                } catch (_: Exception) {
                    continue
                }
                if ((p.name.lowercase() to p.volume) in known) continue
                additions.add(p)
                known.add(p.name.lowercase() to p.volume)
            }
            if (additions.isNotEmpty()) {
                DrinkPresetStore.savePresets(prefs.prefs, existing + additions)
                presetsRestored = additions.size
            }
        }

        // Settings are only filled in where the user hasn't set them: a restore
        // shouldn't quietly rewrite a baseline they've since changed.
        var settingsRestored = false
        json.optJSONObject("settings")?.let { s ->
            if (prefs.baselineDailyMl <= 0 && s.optDouble("baseline_daily_ml", 0.0) > 0) {
                prefs.baselineDailyMl = s.optDouble("baseline_daily_ml")
                settingsRestored = true
            }
            if (prefs.baselineWeeklySpend <= 0f && s.optDouble("baseline_weekly_spend", 0.0) > 0) {
                prefs.baselineWeeklySpend = s.optDouble("baseline_weekly_spend").toFloat()
                settingsRestored = true
            }
            if (prefs.currencyCode.isNullOrBlank()) {
                s.optString("currency_code").takeIf { it.isNotBlank() }?.let {
                    prefs.currencyCode = it
                    settingsRestored = true
                }
            }
            if (prefs.baselineSetDate == null) {
                s.optString("baseline_set_date").takeIf { it.isNotBlank() }?.let {
                    try {
                        prefs.baselineSetDate = LocalDate.parse(it)
                        settingsRestored = true
                    } catch (_: Exception) {
                    }
                }
            }
        }

        return RestoreResult(restored, alreadyPresent, presetsRestored, settingsRestored)
    }
}
