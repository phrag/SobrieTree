package com.sobrietree.android

import android.content.SharedPreferences
import org.json.JSONArray

/** Drink preset persistence, previously buried inside MainActivity. */
object DrinkPresetStore {

    fun getPresets(prefs: SharedPreferences): List<DrinkPreset> {
        val json = prefs.getString("drink_presets", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i -> DrinkPreset.fromJson(arr.getJSONObject(i)) }
        } catch (_: Exception) { emptyList() }
    }

    fun savePresets(prefs: SharedPreferences, presets: List<DrinkPreset>) {
        val arr = JSONArray()
        presets.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("drink_presets", arr.toString()).apply()
    }

    fun addPreset(prefs: SharedPreferences, preset: DrinkPreset) {
        val presets = getPresets(prefs).toMutableList()
        if (presets.none { it.name == preset.name && it.type == preset.type && it.volume == preset.volume && it.strength == preset.strength }) {
            presets.add(preset)
            savePresets(prefs, presets)
        }
    }

    /** Favorite preset first, otherwise the first saved preset. */
    fun defaultPreset(prefs: SharedPreferences): DrinkPreset? {
        val presets = getPresets(prefs)
        return presets.firstOrNull { it.favorite } ?: presets.firstOrNull()
    }
}
