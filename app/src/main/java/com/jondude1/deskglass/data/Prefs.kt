package com.jondude1.deskglass.data

import android.content.Context
import org.json.JSONObject

data class Place(val label: String, val lat: Double, val lon: Double)

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("deskglass", Context.MODE_PRIVATE)

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0L)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

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
