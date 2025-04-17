package com.paul.weatpaper.worker

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.paul.weatpaper.BuildConfig
import com.paul.weatpaper.data.model.WeatherResponse // Upewnij się, że WeatherResponse ma pole 'description' w WeatherInfo
import com.paul.weatpaper.data.remote.ApiClient
import com.paul.weatpaper.utils.LocationProvider
import com.paul.weatpaper.utils.WallpaperChanger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class WallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val PREFS_NAME = "WallpaperWorkerPrefs"
        private const val KEY_LAST_LAT = "last_latitude"
        private const val KEY_LAST_LON = "last_longitude"
        const val PROGRESS_KEY = "work_progress"
        const val ERROR_MESSAGE_KEY = "error_message"
    }

    private val locationProvider = LocationProvider(appContext)
    private val wallpaperChanger = WallpaperChanger(appContext)
    private val apiKey = BuildConfig.OPENWEATHER_API_KEY
    private val sharedPreferences: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        // === Logowanie Startu ===
        Log.d("WallpaperWorker", "Worker started...")
        Firebase.crashlytics.log("WallpaperWorker: doWork started")
        // =========================
        setProgress(0, "Rozpoczynanie pracy...")

        return try {
            // 1. Pobieranie/Sprawdzanie lokalizacji
            Log.d("WallpaperWorker", "Attempting to get location...")
            Firebase.crashlytics.log("WallpaperWorker: Attempting to get location")
            var location: Location? = null
            var locationError: Exception? = null

            try {
                location = locationProvider.getLastLocationSuspend()
                if (location != null) {
                    Firebase.crashlytics.log("WallpaperWorker: getLastLocationSuspend successful. Lat=${location.latitude}, Lon=${location.longitude}")
                } else {
                    Firebase.crashlytics.log("WallpaperWorker: getLastLocationSuspend returned null.")
                }
            } catch (se: SecurityException) {
                locationError = se
                Log.e("WallpaperWorker", "SecurityException during location fetch: ${se.message}", se)
                Firebase.crashlytics.log("WallpaperWorker: SecurityException during location fetch: ${se.message}")
                Firebase.crashlytics.recordException(se)
            } catch (le: Exception) {
                locationError = le
                Log.e("WallpaperWorker", "Exception during location fetch: ${le.message}", le)
                Firebase.crashlytics.log("WallpaperWorker: Exception during location fetch: ${le.message}")
                Firebase.crashlytics.recordException(le)
            }


            var usedStoredLocation = false
            if (location == null) {
                Firebase.crashlytics.log("WallpaperWorker: Current location is null. Trying stored location.")
                Log.w("WallpaperWorker", "Current location is null. Trying stored location.")
                val lastLat = withContext(Dispatchers.IO) { sharedPreferences.getFloat(KEY_LAST_LAT, Float.MAX_VALUE) }
                val lastLon = withContext(Dispatchers.IO) { sharedPreferences.getFloat(KEY_LAST_LON, Float.MAX_VALUE) }

                if (lastLat != Float.MAX_VALUE && lastLon != Float.MAX_VALUE) {
                    location = Location("StoredProvider").apply {
                        latitude = lastLat.toDouble()
                        longitude = lastLon.toDouble()
                    }
                    usedStoredLocation = true
                    Log.i("WallpaperWorker", "Using stored location: Lat=${location.latitude}, Lon=${location.longitude}")
                    Firebase.crashlytics.log("WallpaperWorker: Using stored location: Lat=${location.latitude}, Lon=${location.longitude}")
                } else {
                    Log.e("WallpaperWorker", "Current location is null and no stored location found. Retrying later.")
                    Firebase.crashlytics.log("WallpaperWorker: Current location null and no stored location found. Returning Result.retry()")
                    setProgress(-1, "Błąd lokalizacji")
                    if (locationError != null) {
                        Firebase.crashlytics.log("WallpaperWorker: Failing due to previous location fetch error.")
                        val errorKey = if (locationError is SecurityException) "Brak uprawnień lokalizacji" else "Błąd usług lokalizacyjnych"
                        val outputData = workDataOf(ERROR_MESSAGE_KEY to "$errorKey: ${locationError.localizedMessage}")
                        return Result.failure(outputData)
                    } else {
                        return Result.retry()
                    }
                }
            } else {
                Log.i("WallpaperWorker", "Obtained fresh location: Lat=${location.latitude}, Lon=${location.longitude}. Saving.")
                Firebase.crashlytics.log("WallpaperWorker: Obtained fresh location, saving.")
                withContext(Dispatchers.IO) {
                    with(sharedPreferences.edit()) {
                        putFloat(KEY_LAST_LAT, location.latitude.toFloat())
                        putFloat(KEY_LAST_LON, location.longitude.toFloat())
                        apply()
                    }
                }
            }

            setProgress(33, "Pobieranie pogody...")
            Firebase.crashlytics.log("WallpaperWorker: Progress 33% - Fetching weather")

            // 2. Pobieranie danych pogodowych
            val locationToFetch = location ?: run {
                Firebase.crashlytics.log("WallpaperWorker: Error - location became null before API call. Failing.")
                return Result.failure(workDataOf(ERROR_MESSAGE_KEY to "Niespodziewany błąd - brak lokalizacji przed API"))
            }

            Log.d("WallpaperWorker", "Fetching weather for ${if(usedStoredLocation) "stored" else "current"} location...")
            Firebase.crashlytics.log("WallpaperWorker: Fetching weather for ${if(usedStoredLocation) "stored" else "current"} location: Lat=${locationToFetch.latitude}, Lon=${locationToFetch.longitude}")
            val weatherResponse: Response<WeatherResponse> = fetchWeather(locationToFetch.latitude, locationToFetch.longitude)

            val responseBody = weatherResponse.body()
            val responseCode = weatherResponse.code()
            val responseSuccessful = weatherResponse.isSuccessful
            Firebase.crashlytics.log("WallpaperWorker: API response received. Successful: $responseSuccessful, Code: $responseCode, Body present: ${responseBody != null}")

            if (!responseSuccessful || responseBody == null) {
                val errorBodyString = weatherResponse.errorBody()?.string() ?: "N/A"
                val errorMsg = "API Error: $responseCode - ${weatherResponse.message()} Body: $errorBodyString"
                Log.e("WallpaperWorker", errorMsg)
                Firebase.crashlytics.log("WallpaperWorker: API Error. $errorMsg. Returning Result.retry()")
                setProgress(-1, "Błąd API")
                return Result.retry()
            }

            Log.d("WallpaperWorker", "API Response received successfully and parsed.")
            Firebase.crashlytics.log("WallpaperWorker: API Response successful. Parsing data.")

            // === ZMIANA: BEZPIECZNE PARSOWANIE Z UWZGLĘDNIENIEM 'description' ===
            val weatherInfo = responseBody.weather?.firstOrNull() // Pobierz cały obiekt WeatherInfo
            val weatherDescription = weatherInfo?.description     // <<< Pobierz description
            val weatherMain = weatherInfo?.main                 // <<< Pobierz main (jako fallback lub dodatkowe info)
            val sunrise = responseBody.sys?.sunrise
            val sunset = responseBody.sys?.sunset

            // Sprawdź kluczowe dane - teraz description jest ważne
            if (weatherDescription == null || sunrise == null || sunset == null) {
                val logMessage = "WallpaperWorker: Failed to parse essential data. Description: ${weatherDescription != null}, Sunrise: ${sunrise != null}, Sunset: ${sunset != null}. Body: $responseBody"
                Log.e("WallpaperWorker", logMessage)
                Firebase.crashlytics.log(logMessage)
                Firebase.crashlytics.recordException(IllegalStateException("Failed to parse essential weather data (description, sun times)"))
                setProgress(-1, "Błąd parsowania danych")
                val outputData = workDataOf(ERROR_MESSAGE_KEY to "Błąd parsowania danych API (opis/słońce)")
                return Result.failure(outputData)
            }
            // Zaloguj oba warunki dla jasności
            Log.d("WallpaperWorker", "Weather parsed: Description='$weatherDescription', Main='$weatherMain', Sunrise=$sunrise, Sunset=$sunset")
            Firebase.crashlytics.log("WallpaperWorker: Weather parsed. Description='$weatherDescription', Main='$weatherMain', Sunrise=$sunrise, Sunset=$sunset")
            // =======================================================================

            setProgress(66, "Zmiana tapety...")
            Firebase.crashlytics.log("WallpaperWorker: Progress 66% - Changing wallpaper")

            // 3. Zmiana tapety - przekaż 'weatherDescription' i 'weatherMain'
            Log.d("WallpaperWorker", "Calling WallpaperChanger...")
            Firebase.crashlytics.log("WallpaperWorker: Calling WallpaperChanger")
            // === ZMIANA: Przekazujemy description jako główny warunek, main jako pomocniczy ===
            wallpaperChanger.changeWallpaper(weatherDescription, weatherMain, sunrise, sunset)
            // ============================================================================
            Log.d("WallpaperWorker", "WallpaperChanger finished.")
            Firebase.crashlytics.log("WallpaperWorker: WallpaperChanger finished successfully (within try block)")

            setProgress(100, "Zakończono")
            Log.i("WallpaperWorker", "doWork finished successfully.")
            Firebase.crashlytics.log("WallpaperWorker: Progress 100% - doWork finished successfully. Returning Result.success()")
            return Result.success()

        } catch (e: SecurityException) {
            Log.e("WallpaperWorker", "SecurityException in doWork: ${e.message}", e)
            Firebase.crashlytics.log("WallpaperWorker: Caught SecurityException in main try-catch: ${e.message}")
            Firebase.crashlytics.recordException(e)
            setProgress(-1, "Błąd uprawnień")
            val outputData = workDataOf(ERROR_MESSAGE_KEY to "Brak uprawnień (główny catch)")
            return Result.failure(outputData)

        } catch (e: IOException) {
            Log.e("WallpaperWorker", "IOException during network/file operations: ${e.message}", e)
            Firebase.crashlytics.log("WallpaperWorker: Caught IOException: ${e.message}")
            Firebase.crashlytics.recordException(e)
            setProgress(-1, "Błąd sieci/IO")
            return Result.retry()

        } catch (e: Exception) {
            Log.e("WallpaperWorker", "Generic exception in doWork: ${e.message}", e)
            Firebase.crashlytics.log("WallpaperWorker: Caught generic Exception: ${e.message}")
            Firebase.crashlytics.recordException(e)
            setProgress(-1, "Nieoczekiwany błąd")
            val outputData = workDataOf(ERROR_MESSAGE_KEY to "Nieoczekiwany błąd: ${e.localizedMessage}")
            return Result.failure(outputData)
        }
    } // koniec doWork

    // Funkcja setProgress (bez zmian)
    private suspend fun setProgress(progress: Int, message: String? = null) {
        val dataBuilder = Data.Builder().putInt(PROGRESS_KEY, progress)
        if (message != null) {
            Log.d("WallpaperWorker", "Progress: $progress% - $message")
        } else {
            Log.d("WallpaperWorker", "Progress: $progress%")
        }
        setProgressAsync(dataBuilder.build())
    }


    // Funkcja fetchWeather (bez zmian)
    private suspend fun fetchWeather(lat: Double, lon: Double): Response<WeatherResponse> {
        val weatherApi = ApiClient.getWeatherApi()
        try {
            Firebase.crashlytics.log("WallpaperWorker: fetchWeather - Calling API for lat=$lat, lon=$lon")
            return weatherApi.getWeather(lat, lon, apiKey)
        } catch (e: Exception) {
            Firebase.crashlytics.log("WallpaperWorker: fetchWeather - Exception during API call: ${e.message}")
            Firebase.crashlytics.recordException(e)
            Log.e("WallpaperWorker", "Network/API call failed: ${e.message}")
            throw IOException("Failed to fetch weather data: ${e.message}", e)
        }
    }
}