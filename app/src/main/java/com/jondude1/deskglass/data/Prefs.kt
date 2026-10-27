package com.jondude1.deskglass.data

import android.content.Context
import org.json.JSONObject

data class Place(val label: String, val lat: Double, val lon: Double)

const val DEFAULT_CHAT_MODEL = "claude-sonnet-5-5"
const val DEFAULT_FAST_MODEL = "claude-haiku-5-5"

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("deskglass", Context.MODE_PRIVATE)

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0L)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v.trim()).apply()

    var userName: String
        get() = sp.getString("userName", "Jon") ?: "Jon"
        set(v) = sp.edit().putString("userName", v.trim()).apply()

    var chatModel: String
        get() = sp.getString("chatModel", DEFAULT_CHAT_MODEL) ?: DEFAULT_CHAT_MODEL
        set(v) = sp.edit().putString("chatModel", v.trim().ifEmpty { DEFAULT_CHAT_MODEL }).apply()

    var fastModel: String
        get() = sp.getString("fastModel", DEFAULT_FAST_MODEL) ?: DEFAULT_FAST_MODEL
        set(v) = sp.edit().putString("fastModel", v.trim().ifEmpty { DEFAULT_FAST_MODEL }).apply()

    val idleLine: String get() = sp.getString("idleLine", "") ?: ""
    val idleLineAt: Long get() = sp.getLong("idleLineAt", 0L)

    fun recentIdleLines(): List<String> =
        (sp.getString("recentIdle", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun pushIdleLine(line: String) {
        val all = (recentIdleLines() + line).takeLast(8)
        sp.edit()
            .putString("recentIdle", all.joinToString("\n"))
            .putString("idleLine", line)
            .putLong("idleLineAt", System.currentTimeMillis())
            .apply()
    }

    var driveFolder: String?
        get() = sp.getString("driveFolder", null)
        set(v) = sp.edit().apply { if (v == null) remove("driveFolder") else putString("driveFolder", v) }.apply()

    var driveFolderName: String
        get() = sp.getString("driveFolderName", "") ?: ""
        set(v) = sp.edit().putString("driveFolderName", v).apply()

    var place: Place?
        get() {
            val raw = sp.getString("place", null) ?: return null
            return try {
                val j = JSONObject(raw)
                Place(j.getString("label"), j.getDouble("lat"), j.getDouble("lon"))
            } catch (e: Exception) {
                null
            }
        }
        set(v) {
            val editor = sp.edit()
            if (v == null) {
                editor.remove("place")
            } else {
                editor.putString(
                    "place",
                    JSONObject().put("label", v.label).put("lat", v.lat).put("lon", v.lon).toString()
                )
            }
            editor.apply()
        }
}
