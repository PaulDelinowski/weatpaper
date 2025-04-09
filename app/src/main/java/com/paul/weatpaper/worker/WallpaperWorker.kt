package com.paul.weatpaper.worker

// Potrzebne importy
import android.content.Context
import android.content.SharedPreferences // Import dla SharedPreferences
import android.location.Location
import android.util.Log
// import android.widget.Toast // Usunięty, preferujemy logi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.JsonObject
import com.paul.weatpaper.BuildConfig
import com.paul.weatpaper.data.remote.ApiClient
import com.paul.weatpaper.utils.LocationProvider
import com.paul.weatpaper.utils.WallpaperChanger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class WallpaperWorker(
    val appContext: Context, // Zmień context na appContext dla jasności
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // Klucze do SharedPreferences
    companion object {
        private const val PREFS_NAME = "WallpaperWorkerPrefs"
        private const val KEY_LAST_LAT = "last_latitude"
        private const val KEY_LAST_LON = "last_longitude"
    }

    private val locationProvider = LocationProvider(appContext)
    private val wallpaperChanger = WallpaperChanger(appContext)
    private val apiKey = BuildConfig.OPENWEATHER_API_KEY
    // Instancja SharedPreferences
    private val sharedPreferences: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        Log.d("WallpaperWorker", "Worker started. Attempting to get location...")

        return try {
            // 1. Spróbuj pobrać aktualną lokalizację
            var location: Location? = locationProvider.getLastLocationSuspend() // Zadeklaruj jako var, aby móc przypisać zapisaną
            var usedStoredLocation = false // Flaga informująca, czy użyliśmy zapisanej lokalizacji

            // 2. Jeśli aktualna lokalizacja jest null, spróbuj pobrać ostatnio zapisaną
            if (location == null) {
                Log.w("WallpaperWorker", "Current location is null. Trying to retrieve last known location from SharedPreferences.")
                // Odczytaj zapisane wartości (używamy Float, bo SharedPreferences natywnie to wspiera)
                val lastLat = sharedPreferences.getFloat(KEY_LAST_LAT, Float.MAX_VALUE) // Użyj wartości niemożliwej jako wskaźnik braku
                val lastLon = sharedPreferences.getFloat(KEY_LAST_LON, Float.MAX_VALUE)

                if (lastLat != Float.MAX_VALUE && lastLon != Float.MAX_VALUE) {
                    // Mamy zapisaną lokalizację - utwórz obiekt Location i użyj jej
                    location = Location("StoredProvider").apply {
                        latitude = lastLat.toDouble() // Konwertuj z Float na Double
                        longitude = lastLon.toDouble()
                    }
                    usedStoredLocation = true
                    Log.i("WallpaperWorker", "Using stored location: Lat=${location.latitude}, Lon=${location.longitude}")
                } else {
                    // Nie ma ani aktualnej, ani zapisanej lokalizacji - pomiń cykl (fallback)
                    Log.e("WallpaperWorker", "Current location is null and no stored location found. Skipping update.")
                    return Result.success() // Zakończ sukcesem, ale bez działania
                }
            } else {
                // Mamy świeżą lokalizację - zapisz jej współrzędne na przyszłość
                Log.i("WallpaperWorker", "Obtained fresh location: Lat=${location.latitude}, Lon=${location.longitude}. Saving to SharedPreferences.")
                with(sharedPreferences.edit()) {
                    putFloat(KEY_LAST_LAT, location.latitude.toFloat()) // Zapisz jako Float
                    putFloat(KEY_LAST_LON, location.longitude.toFloat())
                    apply() // Użyj apply() dla zapisu w tle
                }
            }

            // Jeśli doszliśmy tutaj, 'location' zawiera albo świeżą, albo zapisaną lokalizację

            // 3. Wykonaj zapytanie do OpenWeather
            Log.d("WallpaperWorker", "Fetching weather for ${if(usedStoredLocation) "stored" else "current"} location...")
            val weatherResponse = fetchWeather(location.latitude, location.longitude)

            // 4. Odczytaj dane pogodowe z odpowiedzi (bez zmian)
            if (!weatherResponse.isSuccessful) {
                val errorMsg = "API Error: ${weatherResponse.code()} - ${weatherResponse.message()}"
                Log.e("WallpaperWorker", errorMsg)
                return Result.retry() // Błąd API - ponów
            }
            val responseBody = weatherResponse.body()
            Log.d("WallpaperWorker", "API Response: $responseBody") // Ostrożnie z logowaniem całej odpowiedzi
            // ... (parsowanie weatherCondition, sunrise, sunset - bez zmian) ...
            val weatherCondition = responseBody?.getAsJsonArray("weather")?.get(0)?.asJsonObject?.get("main")?.asString
            val sysObject = responseBody?.getAsJsonObject("sys")
            val sunrise = sysObject?.get("sunrise")?.asLong ?: 0L
            val sunset = sysObject?.get("sunset")?.asLong ?: 0L
            if (weatherCondition == null) {
                Log.e("WallpaperWorker", "Weather condition not found in API response")
                return Result.retry() // Błąd parsowania - ponów
            }
            Log.d("WallpaperWorker", "Weather Condition: $weatherCondition, Sunrise: $sunrise, Sunset: $sunset")

            // 5. Zmień tapetę (wywołanie suspend fun)
            Log.d("WallpaperWorker", "Calling WallpaperChanger...")
            wallpaperChanger.changeWallpaper(weatherCondition, sunrise, sunset)
            Log.d("WallpaperWorker", "WallpaperChanger finished.")


            // Jeśli wszystko się udało, zwracamy sukces
            Log.i("WallpaperWorker", "doWork finished successfully.")
            Result.success()

        } catch (e: Exception) {
            // Obsługa innych błędów
            Log.e("WallpaperWorker", "Exception in doWork: ${e.message}", e)
            return Result.retry() // Inny błąd - ponów
        }
    } // Koniec doWork

    // Funkcje fetchWeather i (ewentualnie) showToast bez zmian
    private suspend fun fetchWeather(lat: Double, lon: Double): Response<JsonObject> {
        val weatherApi = ApiClient.getWeatherApi()
        return withContext(Dispatchers.IO) {
            weatherApi.getWeather(lat, lon, apiKey).execute()
        }
    }

    // Funkcja showToast (jeśli nadal istnieje) bez zmian
    /*
    private suspend fun showToast(message: String) {
         withContext(Dispatchers.Main) {
             Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
         }
    }
    */

} // Koniec klasy WallpaperWorker