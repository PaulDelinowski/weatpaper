package com.paul.weatpaper.worker

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data // <<< Potrzebny import dla Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf // <<< Potrzebny import dla workDataOf
import com.google.gson.JsonObject
import com.paul.weatpaper.BuildConfig
import com.paul.weatpaper.data.remote.ApiClient
import com.paul.weatpaper.utils.LocationProvider
import com.paul.weatpaper.utils.WallpaperChanger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class WallpaperWorker(
    val appContext: Context, // Zmieniono context na appContext dla jasności
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // Klucze do SharedPreferences i klucz do postępu
    companion object {
        private const val PREFS_NAME = "WallpaperWorkerPrefs"
        private const val KEY_LAST_LAT = "last_latitude"
        private const val KEY_LAST_LON = "last_longitude"

        // === DODANO: Klucz dla danych postępu ===
        const val PROGRESS_KEY = "work_progress" // Używany w MainActivity do odczytu
        // ========================================
    }

    private val locationProvider = LocationProvider(appContext)
    private val wallpaperChanger = WallpaperChanger(appContext)
    private val apiKey = BuildConfig.OPENWEATHER_API_KEY
    private val sharedPreferences: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        Log.d("WallpaperWorker", "Worker started...")

        // === DODANO: Ustawienie początkowego postępu ===
        setProgress(0, "Rozpoczynanie pracy...") // Użycie nowej funkcji pomocniczej
        // ==============================================

        return try {
            // 1. Pobieranie/Sprawdzanie lokalizacji
            Log.d("WallpaperWorker", "Attempting to get location...")
            var location: Location? = locationProvider.getLastLocationSuspend()
            var usedStoredLocation = false

            if (location == null) {
                Log.w("WallpaperWorker", "Current location is null. Trying stored location.")
                val lastLat = sharedPreferences.getFloat(KEY_LAST_LAT, Float.MAX_VALUE)
                val lastLon = sharedPreferences.getFloat(KEY_LAST_LON, Float.MAX_VALUE)

                if (lastLat != Float.MAX_VALUE && lastLon != Float.MAX_VALUE) {
                    location = Location("StoredProvider").apply {
                        latitude = lastLat.toDouble()
                        longitude = lastLon.toDouble()
                    }
                    usedStoredLocation = true
                    Log.i("WallpaperWorker", "Using stored location: Lat=${location.latitude}, Lon=${location.longitude}")
                } else {
                    // === ZMIANA: Zwróć retry, jeśli brak lokalizacji ===
                    Log.e("WallpaperWorker", "Current location is null and no stored location found. Retrying later.")
                    setProgress(-1, "Błąd lokalizacji") // Ustawienie postępu na -1 jako wskaźnik błędu
                    return Result.retry() // Spróbuj ponownie później
                    // =====================================================
                }
            } else {
                // Świeża lokalizacja uzyskana - zapisz ją
                Log.i("WallpaperWorker", "Obtained fresh location: Lat=${location.latitude}, Lon=${location.longitude}. Saving.")
                with(sharedPreferences.edit()) {
                    putFloat(KEY_LAST_LAT, location.latitude.toFloat())
                    putFloat(KEY_LAST_LON, location.longitude.toFloat())
                    apply()
                }
            }

            // === DODANO: Postęp po uzyskaniu lokalizacji ===
            setProgress(33, "Pobieranie pogody...")
            // ===============================================

            // 2. Pobieranie danych pogodowych
            Log.d("WallpaperWorker", "Fetching weather for ${if(usedStoredLocation) "stored" else "current"} location...")
            val weatherResponse = fetchWeather(location.latitude, location.longitude)

            // Sprawdzanie odpowiedzi API
            if (!weatherResponse.isSuccessful) {
                val errorMsg = "API Error: ${weatherResponse.code()} - ${weatherResponse.message()} Body: ${weatherResponse.errorBody()?.string()}"
                Log.e("WallpaperWorker", errorMsg)
                setProgress(-1, "Błąd API") // Opcjonalnie: wskaźnik błędu
                return Result.retry() // Błąd API - ponów próbę
            }
            val responseBody = weatherResponse.body()
            if (responseBody == null) {
                Log.e("WallpaperWorker", "API response body is null.")
                setProgress(-1, "Błąd odpowiedzi API") // Opcjonalnie: wskaźnik błędu
                return Result.retry() // Błąd API - ponów próbę
            }

            Log.d("WallpaperWorker", "API Response received successfully.") // Logujemy sukces, nie całą odpowiedź

            // Parsowanie odpowiedzi
            val weatherCondition = responseBody.getAsJsonArray("weather")?.get(0)?.asJsonObject?.get("main")?.asString
            val sysObject = responseBody.getAsJsonObject("sys")
            val sunrise = sysObject?.get("sunrise")?.asLong ?: 0L
            val sunset = sysObject?.get("sunset")?.asLong ?: 0L

            if (weatherCondition == null || sunrise == 0L || sunset == 0L) {
                Log.e("WallpaperWorker", "Failed to parse weather condition, sunrise, or sunset from API response.")
                setProgress(-1, "Błąd parsowania danych") // Opcjonalnie: wskaźnik błędu
                return Result.retry() // Błąd parsowania - ponów próbę
            }
            Log.d("WallpaperWorker", "Weather parsed: Condition=$weatherCondition, Sunrise=$sunrise, Sunset=$sunset")


            // === DODANO: Postęp po pobraniu i sparsowaniu pogody ===
            setProgress(66, "Zmiana tapety...")
            // ======================================================

            // 3. Zmiana tapety
            Log.d("WallpaperWorker", "Calling WallpaperChanger...")
            wallpaperChanger.changeWallpaper(weatherCondition, sunrise, sunset) // Wywołanie suspend fun
            Log.d("WallpaperWorker", "WallpaperChanger finished.")

            // === DODANO: Postęp końcowy ===
            setProgress(100, "Zakończono")
            // ==============================

            Log.i("WallpaperWorker", "doWork finished successfully.")
            Result.success() // Wszystko poszło dobrze

        } catch (e: SecurityException) {
            // Specyficzna obsługa błędu braku uprawnień (może się zdarzyć, jeśli uprawnienia zostaną cofnięte)
            Log.e("WallpaperWorker", "SecurityException in doWork (Permissions likely revoked): ${e.message}", e)
            setProgress(-1, "Błąd uprawnień")
            return Result.failure() // Nie ponawiaj, jeśli nie ma uprawnień - błąd krytyczny
        }
        catch (e: Exception) {
            // Obsługa innych, nieoczekiwanych błędów
            Log.e("WallpaperWorker", "Generic exception in doWork: ${e.message}", e)
            setProgress(-1, "Nieoczekiwany błąd") // Opcjonalnie: wskaźnik błędu
            return Result.retry() // Inny błąd - spróbuj ponownie
        }
    } // Koniec doWork

    // Funkcja pomocnicza do ustawiania postępu (aby uniknąć powtarzania kodu)
    private suspend fun setProgress(progress: Int, message: String? = null) {
        val dataBuilder = Data.Builder().putInt(PROGRESS_KEY, progress)
        if (message != null) {
            // Można by dodać też wiadomość do danych, jeśli UI miałoby ją wyświetlać
            // dataBuilder.putString("progress_message", message)
            Log.d("WallpaperWorker", "Progress: $progress% - $message")
        } else {
            Log.d("WallpaperWorker", "Progress: $progress%")
        }
        setProgressAsync(dataBuilder.build())
    }


    // Pobiera dane pogodowe z API (bez zmian)
    private suspend fun fetchWeather(lat: Double, lon: Double): Response<JsonObject> {
        val weatherApi = ApiClient.getWeatherApi()
        // Wykonanie zapytania w kontekście IO
        return withContext(Dispatchers.IO) {
            try {
                // Używamy .execute() dla synchronicznego wykonania w ramach korutyny
                weatherApi.getWeather(lat, lon, apiKey).execute()
            } catch (e: Exception) {
                // Złap błędy sieciowe/retrofit i zwróć je jako błąd (np. poprzez rzucenie wyjątku lub opakowanie wyniku)
                // Tutaj rzucamy dalej, aby główny try-catch w doWork mógł obsłużyć
                Log.e("WallpaperWorker", "Network/API call failed: ${e.message}")
                throw IOException("Failed to fetch weather data", e) // Opakuj w IOException dla jasności
            }
        }
    }

} // Koniec klasy WallpaperWorker