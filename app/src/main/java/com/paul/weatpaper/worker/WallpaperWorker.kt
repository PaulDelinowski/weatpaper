package com.paul.weatpaper.worker

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
// import com.google.gson.JsonObject // Już niepotrzebny
import com.paul.weatpaper.BuildConfig
import com.paul.weatpaper.data.model.WeatherResponse // <<< Zaimportuj nową klasę
import com.paul.weatpaper.data.remote.ApiClient
import com.paul.weatpaper.utils.LocationProvider
import com.paul.weatpaper.utils.WallpaperChanger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response // <<< Upewnij się, że Response jest zaimportowane
import java.io.IOException

class WallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // ... (Companion object bez zmian) ...
    companion object {
        private const val PREFS_NAME = "WallpaperWorkerPrefs"
        private const val KEY_LAST_LAT = "last_latitude"
        private const val KEY_LAST_LON = "last_longitude"
        const val PROGRESS_KEY = "work_progress"
    }

    private val locationProvider = LocationProvider(appContext)
    private val wallpaperChanger = WallpaperChanger(appContext)
    private val apiKey = BuildConfig.OPENWEATHER_API_KEY
    private val sharedPreferences: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        Log.d("WallpaperWorker", "Worker started...")
        setProgress(0, "Rozpoczynanie pracy...")

        return try {
            // 1. Pobieranie/Sprawdzanie lokalizacji (bez zmian w tej sekcji)
            Log.d("WallpaperWorker", "Attempting to get location...")
            var location: Location? = locationProvider.getLastLocationSuspend()
            var usedStoredLocation = false

            if (location == null) {
                Log.w("WallpaperWorker", "Current location is null. Trying stored location.")
                // --- Sugestia: Dostęp do SharedPreferences w IO context ---
                val lastLat = withContext(Dispatchers.IO) { sharedPreferences.getFloat(KEY_LAST_LAT, Float.MAX_VALUE) }
                val lastLon = withContext(Dispatchers.IO) { sharedPreferences.getFloat(KEY_LAST_LON, Float.MAX_VALUE) }
                // -----------------------------------------------------------

                if (lastLat != Float.MAX_VALUE && lastLon != Float.MAX_VALUE) {
                    location = Location("StoredProvider").apply {
                        latitude = lastLat.toDouble()
                        longitude = lastLon.toDouble()
                    }
                    usedStoredLocation = true
                    Log.i("WallpaperWorker", "Using stored location: Lat=${location.latitude}, Lon=${location.longitude}")
                } else {
                    Log.e("WallpaperWorker", "Current location is null and no stored location found. Retrying later.")
                    setProgress(-1, "Błąd lokalizacji")
                    return Result.retry()
                }
            } else {
                Log.i("WallpaperWorker", "Obtained fresh location: Lat=${location.latitude}, Lon=${location.longitude}. Saving.")
                // --- Sugestia: Dostęp do SharedPreferences w IO context ---
                withContext(Dispatchers.IO) {
                    with(sharedPreferences.edit()) {
                        putFloat(KEY_LAST_LAT, location.latitude.toFloat())
                        putFloat(KEY_LAST_LON, location.longitude.toFloat())
                        apply()
                    }
                }
                // -----------------------------------------------------------
            }

            setProgress(33, "Pobieranie pogody...")

            // 2. Pobieranie danych pogodowych (ZMIANY TUTAJ)
            Log.d("WallpaperWorker", "Fetching weather for ${if(usedStoredLocation) "stored" else "current"} location...")
            val weatherResponse: Response<WeatherResponse> = fetchWeather(location.latitude, location.longitude) // Zmieniony typ

            // Sprawdzanie odpowiedzi API
            val responseBody = weatherResponse.body() // Teraz typu WeatherResponse?
            if (!weatherResponse.isSuccessful || responseBody == null) {
                val errorMsg = "API Error: ${weatherResponse.code()} - ${weatherResponse.message()} Body: ${weatherResponse.errorBody()?.string()}"
                Log.e("WallpaperWorker", errorMsg)
                setProgress(-1, "Błąd API")
                return Result.retry() // Błąd API - ponów próbę
            }

            Log.d("WallpaperWorker", "API Response received successfully and parsed.") // Zaktualizowany log

            // === NOWE BEZPIECZNE PARSOWANIE ===
            // Używamy bezpiecznych wywołań (?.) i sprawdzamy null
            val weatherCondition = responseBody.weather?.firstOrNull()?.main
            val sunrise = responseBody.sys?.sunrise
            val sunset = responseBody.sys?.sunset

            // Sprawdź, czy udało się sparsować wszystkie potrzebne dane
            if (weatherCondition == null || sunrise == null || sunset == null) {
                Log.e("WallpaperWorker", "Failed to parse essential weather data (condition, sunrise, or sunset) from API response. Body: $responseBody")
                setProgress(-1, "Błąd parsowania danych")
                return Result.retry() // Błąd parsowania - ponów próbę
            }
            Log.d("WallpaperWorker", "Weather parsed: Condition=$weatherCondition, Sunrise=$sunrise, Sunset=$sunset")
            // ===================================

            setProgress(66, "Zmiana tapety...")

            // 3. Zmiana tapety (bez zmian w tej sekcji)
            Log.d("WallpaperWorker", "Calling WallpaperChanger...")
            wallpaperChanger.changeWallpaper(weatherCondition, sunrise, sunset) // Przekazujemy już bezpiecznie rozpakowane wartości
            Log.d("WallpaperWorker", "WallpaperChanger finished.")

            setProgress(100, "Zakończono")
            Log.i("WallpaperWorker", "doWork finished successfully.")
            Result.success()

        } catch (e: SecurityException) {
            Log.e("WallpaperWorker", "SecurityException in doWork (Permissions likely revoked): ${e.message}", e)
            setProgress(-1, "Błąd uprawnień")
            return Result.failure()
        } catch (e: IOException) { // Łapiemy IOException z fetchWeather
            Log.e("WallpaperWorker", "IOException during weather fetch or file operations: ${e.message}", e)
            setProgress(-1, "Błąd sieci/IO")
            return Result.retry() // Błąd sieci/IO - spróbuj ponownie
        }
        catch (e: Exception) {
            Log.e("WallpaperWorker", "Generic exception in doWork: ${e.message}", e)
            setProgress(-1, "Nieoczekiwany błąd")
            return Result.retry()
        }
    }

    // ... (Funkcja setProgress bez zmian) ...
    private suspend fun setProgress(progress: Int, message: String? = null) {
        val dataBuilder = Data.Builder().putInt(PROGRESS_KEY, progress)
        if (message != null) {
            Log.d("WallpaperWorker", "Progress: $progress% - $message")
        } else {
            Log.d("WallpaperWorker", "Progress: $progress%")
        }
        setProgressAsync(dataBuilder.build())
    }


    // === ZMIENIONA FUNKCJA fetchWeather ===
    private suspend fun fetchWeather(lat: Double, lon: Double): Response<WeatherResponse> { // Zmieniony typ zwracany
        val weatherApi = ApiClient.getWeatherApi()
        // Wykonanie zapytania - Retrofit obsługuje wątki dla suspend fun
        // Nie potrzebujemy już withContext(Dispatchers.IO) tutaj
        try {
            // Bezpośrednie wywołanie suspend function
            return weatherApi.getWeather(lat, lon, apiKey)
        } catch (e: Exception) {
            // Złap błędy sieciowe/retrofit (np. brak połączenia, błąd DNS)
            // Rzuć jako IOException, aby główny try-catch w doWork mógł go obsłużyć jako retry
            Log.e("WallpaperWorker", "Network/API call failed during suspend fun execution: ${e.message}")
            throw IOException("Failed to fetch weather data: ${e.message}", e)
        }
    }
    // =====================================

}