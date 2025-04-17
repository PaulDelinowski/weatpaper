package com.paul.weatpaper.utils

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class WallpaperChanger(private val context: Context) {

    /**
     * Zmienia tapetę na podstawie pogody (description) i pory dnia.
     * Używa `description` do dokładniejszego mapowania.
     * `mainCondition` może służyć jako fallback.
     */
    // === ZMIANA: Zaktualizowana sygnatura funkcji ===
    suspend fun changeWallpaper(
        weatherDescription: String, // <<< Główny warunek z API (np. "few clouds")
        mainCondition: String?,     // <<< Ogólny warunek (np. "Clouds") - opcjonalny fallback
        sunrise: Long,
        sunset: Long
    ) {
        // ============================================
        // --- Logika ustalania pory dnia (bez zmian) ---
        val currentTime = System.currentTimeMillis() / 1000
        val sunrise_period_end = sunrise + 3600
        val sunset_period_start = sunset - 7200
        val partOfDay = when {
            currentTime >= sunrise && currentTime < sunrise_period_end -> "sunrise"
            currentTime >= sunset_period_start && currentTime < sunset -> "sunset"
            currentTime >= sunrise_period_end && currentTime < sunset_period_start -> "day"
            else -> "night"
        }
        Log.d("WallpaperChanger", "Current time: $currentTime, Sunrise: $sunrise, Sunset: $sunset, Calculated partOfDay: $partOfDay")

        Log.d("WallpaperChanger", "Checking weather description before mapping: '$weatherDescription', main: '$mainCondition'")

        // --- ZMIANA: NOWA LOGIKA MAPOWANIA POGODY NA FOLDER (używa description) ---
        val weatherFolder = when {
            // --- Czysto / Lekkie chmury ---
            weatherDescription.equals("clear sky", ignoreCase = true) -> "clear"
            weatherDescription.equals("few clouds", ignoreCase = true) -> "clear" // Lekkie chmury -> folder clear
            weatherDescription.equals("scattered clouds", ignoreCase = true) -> "clear" // Rozproszone chmury -> folder clear

            // --- Zachmurzenie ---
            weatherDescription.equals("broken clouds", ignoreCase = true) -> "cloud" // Duże zachmurzenie -> folder cloud
            weatherDescription.equals("overcast clouds", ignoreCase = true) -> "cloud" // Całkowite zachmurzenie -> folder cloud
            mainCondition?.equals("Clouds", ignoreCase = true) == true &&
                    !weatherDescription.contains("rain", ignoreCase = true) &&
                    !weatherDescription.contains("snow", ignoreCase = true) -> "cloud"

            // --- Deszcz / Mżawka ---
            weatherDescription.contains("rain", ignoreCase = true) -> "rain"
            weatherDescription.contains("drizzle", ignoreCase = true) -> "rain"
            mainCondition?.equals("Rain", ignoreCase = true) == true -> "rain"
            mainCondition?.equals("Drizzle", ignoreCase = true) == true -> "rain"

            // --- Burza ---
            weatherDescription.contains("thunderstorm", ignoreCase = true) -> "rain"
            mainCondition?.equals("Thunderstorm", ignoreCase = true) == true -> "rain"

            // --- Śnieg ---
            weatherDescription.contains("snow", ignoreCase = true) -> "clear" // Załóżmy clear na razie
            mainCondition?.equals("Snow", ignoreCase = true) == true -> "clear"

            // --- Mgła / Mętność itp. ---
            weatherDescription.contains("mist", ignoreCase = true) ||
                    weatherDescription.contains("smoke", ignoreCase = true) ||
                    weatherDescription.contains("haze", ignoreCase = true) ||
                    weatherDescription.contains("fog", ignoreCase = true) -> "clear"
            mainCondition?.let { it == "Mist" || it == "Smoke" || it == "Haze" || it == "Fog" } == true -> "clear"

            // --- Domyślnie: Czysto ---
            else -> {
                Log.w("WallpaperChanger", "Unhandled weather description: '$weatherDescription' (main: '$mainCondition'). Defaulting to 'clear'.")
                "clear"
            }
        }
        // =====================================================================
        Log.d("WallpaperChanger", "Mapped weather description '$weatherDescription' to folder '$weatherFolder'")

        // --- Ustalanie ścieżki i zmiana tapety (bez zmian) ---
        val folderPath = "$weatherFolder/$partOfDay"
        Log.d("WallpaperChanger", "Attempting to load wallpaper from assets path: $folderPath")

        try {
            withContext(Dispatchers.IO) {
                val assetManager = context.assets
                val wallpapers = assetManager.list(folderPath) ?: emptyArray()
                Log.d("WallpaperChanger", "Available wallpapers in '$folderPath': ${wallpapers.joinToString(", ")}")

                if (wallpapers.isNotEmpty()) {
                    val randomWallpaper = wallpapers.random()
                    Log.d("WallpaperChanger", "Selected random wallpaper: $randomWallpaper")
                    assetManager.open("$folderPath/$randomWallpaper").use { inputStream ->
                        WallpaperManager.getInstance(context).setStream(inputStream)
                        Log.i("WallpaperChanger", "Wallpaper successfully set from: $folderPath/$randomWallpaper")
                    }
                } else {
                    Log.w("WallpaperChanger", "No wallpapers found in assets path: $folderPath")
                }
            }
        } catch (e: IOException) {
            Log.e("WallpaperChanger", "IOException while changing wallpaper from path '$folderPath'", e)
            // throw e
        } catch (e: Exception) {
            Log.e("WallpaperChanger", "Unexpected error while changing wallpaper: ${e.message}", e)
            // throw e
        }
    } // Koniec funkcji changeWallpaper
} // Koniec klasy WallpaperChanger