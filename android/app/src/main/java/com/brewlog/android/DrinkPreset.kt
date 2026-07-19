package com.brewlog.android

import org.json.JSONObject

enum class DrinkType(val displayName: String) {
    BEER("Beer"),
    WINE("Wine"),
    SPIRITS("Spirits"),
    CUSTOM("Custom");
    companion object {
        fun fromString(value: String): DrinkType = values().find { it.name == value } ?: CUSTOM
    }
}

data class DrinkPreset(
    val name: String,
    val type: DrinkType,
    val volume: Int,
    val strength: Float,
    val favorite: Boolean = false,
    /** What one of these drinks costs the user; 0 = not set (falls back to the global price). */
    val cost: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("type", type.name)
        put("volume", volume)
        put("strength", strength)
        put("favorite", favorite)
        put("cost", cost)
    }
    companion object {
        fun fromJson(obj: JSONObject): DrinkPreset = DrinkPreset(
            obj.getString("name"),
            DrinkType.fromString(obj.optString("type", "CUSTOM")),
            obj.getInt("volume"),
            obj.getDouble("strength").toFloat(),
            obj.optBoolean("favorite", false),
            obj.optDouble("cost", 0.0).toFloat()
        )
    }
}
