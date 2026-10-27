package com.jondude1.deskglass.data

import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WeatherNow(
    val label: String,
    val tempF: Int,
    val feelsF: Int,
    val hiF: Int,
    val loF: Int,
    val windMph: Int,
    val code: Int,
    val isDay: Boolean,
)

enum class WeatherKind { Clear, Night, PartlyCloudy, Cloudy, Fog, Rain, Snow, Storm }

fun weatherKind(code: Int, isDay: Boolean): WeatherKind = when (code) {
    0 -> if (isDay) WeatherKind.Clear else WeatherKind.Night
    1, 2 -> WeatherKind.PartlyCloudy
    3 -> WeatherKind.Cloudy
    45, 48 -> WeatherKind.Fog
    in 51..67, in 80..82 -> WeatherKind.Rain
    in 71..77, 85, 86 -> WeatherKind.Snow
    95, 96, 99 -> WeatherKind.Storm
    else -> WeatherKind.Cloudy
}

fun weatherText(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71 -> "Light snow"
    73 -> "Snow"
    75 -> "Heavy snow"
    77 -> "Snow grains"
    80, 81 -> "Rain showers"
    82 -> "Heavy showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Cloudy"
}

/** Weather from Open-Meteo, which needs no account or key. */
object WeatherApi {
    /** "Erie" or "Erie, Pennsylvania". The part after the comma narrows the match when it can. */
    suspend fun geocode(input: String): Place? = withContext(Dispatchers.IO) {
        val parts = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val name = parts.firstOrNull() ?: return@withContext null
        val hint = parts.getOrNull(1)
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(name, "UTF-8") + "&count=10&language=en&format=json"
        val results = JSONObject(Http.get(url)).optJSONArray("results") ?: return@withContext null
        var chosen: JSONObject? = null
        if (hint != null) {
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val admin = r.optString("admin1")
                val country = r.optString("country")
                val cc = r.optString("country_code")
                if (admin.startsWith(hint, true) || country.startsWith(hint, true) || cc.equals(hint, true)) {
                    chosen = r
                    break
                }
            }
        }
        val r = chosen ?: results.getJSONObject(0)
        val label = listOf(r.optString("name"), r.optString("admin1"))
            .filter { it.isNotEmpty() }
            .joinToString(", ")
        Place(label, r.getDouble("latitude"), r.getDouble("longitude"))
    }

    suspend fun fetch(place: Place): WeatherNow = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=1"
        val j = JSONObject(Http.get(url))
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        WeatherNow(
            label = place.label,
            tempF = cur.getDouble("temperature_2m").roundToInt(),
            feelsF = cur.getDouble("apparent_temperature").roundToInt(),
            hiF = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            loF = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            windMph = cur.getDouble("wind_speed_10m").roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.getInt("is_day") == 1,
        )
    }
}
