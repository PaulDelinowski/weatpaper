package com.paul.weatpaper.utils

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
// Usunięty import Toast: import android.widget.Toast
import kotlinx.coroutines.Dispatchers // Potrzebny import dla withContext
import kotlinx.coroutines.withContext
import java.io.IOException

class WallpaperChanger(private val context: Context) {

    /**
     * Zmienia tapetę na podstawie pogody i pory dnia.
     * Używa nowej logiki czasowej:
     * - sunrise: od wschodu przez 1h
     * - sunset: od 1h przed zachodem do zachodu
     * - day: pomiędzy końcem 'sunrise' a początkiem 'sunset'
     * - night: od zachodu do wschodu
     *
     * Funkcja jest teraz 'suspend' dla lepszej integracji z CoroutineWorker.
     */
    suspend fun changeWallpaper(weatherCondition: String, sunrise: Long, sunset: Long) {
        val currentTime = System.currentTimeMillis() / 1000 // Aktualny czas w sekundach

        // Oblicz granice nowych okresów
        val sunrise_period_end = sunrise + 3600      // Koniec okresu "sunrise" = 1 godzina po wschodzie
        val sunset_period_start = sunset - 7200     // Początek okresu "sunset" = 1 godzina przed zachodem

        // --- NOWA LOGIKA USTALANIA PORY DNIA ---
        val partOfDay = when {
            // Okres "Sunrise": Od wschodu do godziny po wschodzie
            currentTime >= sunrise && currentTime < sunrise_period_end -> "sunrise"

            // Okres "Sunset": Od godziny przed zachodem do momentu zachodu
            currentTime >= sunset_period_start && currentTime < sunset -> "sunset"

            // Okres "Day": Pomiędzy końcem okresu "sunrise" a początkiem okresu "sunset"
            currentTime >= sunrise_period_end && currentTime < sunset_period_start -> "day"

            // Okres "Night": Cała reszta (od zachodu do wschodu)
            else -> "night"
        }
        // Logowanie dla weryfikacji obliczonej pory dnia
        Log.d("WallpaperChanger", "Current time: $currentTime, Sunrise: $sunrise, Sunset: $sunset, Calculated partOfDay: $partOfDay")

        // --- Logika mapowania pogody na folder (bez zmian z Twojego oryginalnego kodu) ---
        val weatherFolder = when {
            weatherCondition.contains("clear", ignoreCase = true) -> "clear"
            weatherCondition.contains("cloud", ignoreCase = true) -> "cloud"
            weatherCondition.contains("rain", ignoreCase = true) ||
                    weatherCondition.contains("drizzle", ignoreCase = true) -> "rain"
            // Możesz dodać więcej warunków pogodowych (Snow, Mist, Fog, etc.) tutaj, jeśli chcesz
            else -> "clear" // Domyślna, jeśli pogoda nie pasuje do powyższych
        }
        Log.d("WallpaperChanger", "Mapped weather condition '$weatherCondition' to folder '$weatherFolder'")

        // --- Ustalanie ścieżki i zmiana tapety (z usprawnioną obsługą korutyn) ---
        val folderPath = "$weatherFolder/$partOfDay"
        Log.d("WallpaperChanger", "Attempting to load wallpaper from assets path: $folderPath")

        try {
            // Operacje wejścia/wyjścia (listowanie plików, otwieranie strumienia, ustawianie tapety)
            // powinny być wykonane poza głównym wątkiem. Używamy withContext(Dispatchers.IO).
            withContext(Dispatchers.IO) {
                val assetManager = context.assets
                // assetManager.list może zwrócić null, jeśli ścieżka nie istnieje
                val wallpapers = assetManager.list(folderPath) ?: emptyArray()
                Log.d("WallpaperChanger", "Available wallpapers in '$folderPath': ${wallpapers.joinToString(", ")}")

                if (wallpapers.isNotEmpty()) {
                    val randomWallpaper = wallpapers.random()
                    Log.d("WallpaperChanger", "Selected random wallpaper: $randomWallpaper")
                    // Otwieranie strumienia i ustawianie tapety
                    assetManager.open("$folderPath/$randomWallpaper").use { inputStream ->
                        // WallpaperManager.getInstance() można bezpiecznie wywołać tutaj
                        // setStream jest operacją blokującą, dlatego jest w Dispatchers.IO
                        WallpaperManager.getInstance(context).setStream(inputStream)
                        // Logujemy sukces
                        Log.i("WallpaperChanger", "Wallpaper successfully set from: $folderPath/$randomWallpaper")
                    }
                } else {
                    // Logujemy ostrzeżenie, jeśli folder jest pusty lub nie istnieje
                    Log.w("WallpaperChanger", "No wallpapers found in assets path: $folderPath")
                    // Nie pokazujemy Toastu użytkownikowi z zadania w tle
                }
            } // Koniec withContext(Dispatchers.IO)
        } catch (e: IOException) {
            // Logujemy błędy związane z operacjami na plikach/strumieniach
            Log.e("WallpaperChanger", "IOException while changing wallpaper from path '$folderPath'", e)
            // Możesz rozważyć rzucenie wyjątku dalej, jeśli chcesz, aby worker zwrócił Result.failure()
            // throw e
        } catch (e: Exception) {
            // Logujemy inne, nieoczekiwane błędy
            Log.e("WallpaperChanger", "Unexpected error while changing wallpaper: ${e.message}", e)
            // throw e // Opcjonalnie
        }
    } // Koniec funkcji changeWallpaper
} // Koniec klasy WallpaperChanger