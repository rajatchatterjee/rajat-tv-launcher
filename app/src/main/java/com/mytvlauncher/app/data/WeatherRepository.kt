package com.mytvlauncher.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherInfo(val tempC: Int, val weatherCode: Int)

/**
 * Pulls a one-shot current-conditions reading from Open-Meteo, a free
 * weather API that needs no API key or account — a good fit for a
 * side project that shouldn't depend on managing secrets. Returns null
 * on any failure (no network, bad coordinates, timeout) rather than
 * throwing, since a missing weather widget should never break the
 * home screen.
 */
class WeatherRepository {
    suspend fun fetchCurrentWeather(lat: Double, lon: Double): WeatherInfo? {
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"

            if (connection.responseCode != 200) return null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current_weather")
            WeatherInfo(
                tempC = current.getDouble("temperature").toInt(),
                weatherCode = current.getInt("weathercode")
            )
        } catch (e: Exception) {
            null
        }
    }
}
