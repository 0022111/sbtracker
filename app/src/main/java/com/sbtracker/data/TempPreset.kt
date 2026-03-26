package com.sbtracker.data

import org.json.JSONArray
import org.json.JSONObject

data class TempPreset(val name: String, val tempC: Int)

object TempPresetSerializer {
    private val defaults = listOf(
        TempPreset("Low", 170),
        TempPreset("Medium", 185),
        TempPreset("High", 200)
    )

    fun toJson(presets: List<TempPreset>): String {
        val arr = JSONArray()
        presets.forEach { p ->
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("tempC", p.tempC)
            })
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<TempPreset> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                TempPreset(obj.getString("name"), obj.getInt("tempC"))
            }
        } catch (e: Exception) {
            defaults
        }
    }

    fun defaultJson(): String = toJson(defaults)
}
