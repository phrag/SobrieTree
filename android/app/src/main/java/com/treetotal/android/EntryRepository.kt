package com.treetotal.android

import org.json.JSONArray
import java.time.LocalDate

/**
 * Single access point for drink entries. Wraps the native (Rust/SQLite) layer and
 * falls back to the in-memory [TreeTotal] when the native library is unavailable,
 * matching the behaviour previously copy-pasted across every activity.
 */
class EntryRepository(private val treeTotal: TreeTotal = TreeTotalProvider.instance) {

    fun getEntries(start: LocalDate, end: LocalDate): List<BeerEntry> {
        val json = try {
            TreeTotalNative.get_beer_entries_json(start.toString(), end.toString())
        } catch (_: Throwable) { "" }
        return if (json.startsWith("[")) {
            parseEntries(json)
        } else {
            treeTotal.getBeerEntries(start.toString(), end.toString())
        }
    }

    /** Alcohol-only ml totals per day (entries with 0% ABV are ignored). */
    fun getDailyTotals(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val map = HashMap<LocalDate, Double>()
        for (entry in getEntries(start, end)) {
            if (entry.alcoholPercentage <= 0) continue
            val date = try { LocalDate.parse(entry.date) } catch (_: Exception) { continue }
            map[date] = (map[date] ?: 0.0) + entry.volumeMl
        }
        return map
    }

    fun getDailyConsumption(date: LocalDate): Double = try {
        val v = TreeTotalNative.get_daily_consumption(date.toString())
        if (v >= 0) v else treeTotal.getDailyConsumption(date)
    } catch (_: Throwable) {
        treeTotal.getDailyConsumption(date)
    }

    fun getWeeklyConsumption(weekStart: LocalDate): Double = try {
        val v = TreeTotalNative.get_weekly_consumption(weekStart.toString())
        if (v >= 0) v else treeTotal.getWeeklyConsumption(weekStart)
    } catch (_: Throwable) {
        treeTotal.getWeeklyConsumption(weekStart)
    }

    fun addEntry(name: String, alcoholPercentage: Double, volumeMl: Double, notes: String) {
        val r = try {
            TreeTotalNative.add_beer_entry(name, alcoholPercentage, volumeMl, notes)
        } catch (_: Throwable) { "" }
        if (!r.startsWith("OK")) {
            treeTotal.addBeerEntry(name, alcoholPercentage, volumeMl, notes)
        }
    }

    fun addEntryAt(date: LocalDate, name: String, alcoholPercentage: Double, volumeMl: Double, notes: String): Boolean =
        addEntryAtReturningId(date, name, alcoholPercentage, volumeMl, notes) != null

    /**
     * Same as [addEntryAt] but hands back the new entry's id, so a caller that
     * logged without a confirmation step (the widget) can offer to undo it.
     * Returns null when the write failed.
     */
    fun addEntryAtReturningId(
        date: LocalDate,
        name: String,
        alcoholPercentage: Double,
        volumeMl: Double,
        notes: String
    ): String? {
        val id = java.util.UUID.randomUUID().toString()
        val r = try {
            TreeTotalNative.add_beer_entry_full_jni(id, name, alcoholPercentage, volumeMl, date.toString(), notes)
        } catch (_: Throwable) { "" }
        return if (r.startsWith("OK")) id else null
    }

    fun updateEntry(id: String, name: String, alcoholPercentage: Double, volumeMl: Double, notes: String) {
        val r = try {
            TreeTotalNative.update_beer_entry_jni(id, name, alcoholPercentage, volumeMl, notes)
        } catch (_: Throwable) { "" }
        if (!r.startsWith("OK")) {
            treeTotal.updateBeerEntry(id, name, alcoholPercentage, volumeMl, notes)
        }
    }

    fun updateEntryDate(id: String, date: LocalDate) {
        val r = try {
            TreeTotalNative.update_beer_entry_date_jni(id, date.toString())
        } catch (_: Throwable) { "" }
        if (!r.startsWith("OK")) {
            treeTotal.updateBeerEntryDate(id, date)
        }
    }

    fun deleteEntry(id: String) {
        val r = try {
            TreeTotalNative.delete_beer_entry_jni(id)
        } catch (_: Throwable) { "" }
        if (!r.startsWith("OK")) {
            treeTotal.deleteBeerEntry(id)
        }
    }

    companion object {
        fun parseEntries(json: String): List<BeerEntry> = try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                BeerEntry(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    alcoholPercentage = o.optDouble("alcohol_percentage", o.optDouble("alcoholPercentage", 0.0)),
                    volumeMl = o.optDouble("volume_ml", o.optDouble("volumeMl", 0.0)),
                    date = o.optString("date"),
                    notes = o.optString("notes", "")
                )
            }
        } catch (_: Throwable) { emptyList() }
    }
}
