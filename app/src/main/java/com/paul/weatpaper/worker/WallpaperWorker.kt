package com.paul.weatpaper.worker

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.JsonObject
import com.paul.weatpaper.data.remote.ApiClient
import com.paul.weatpaper.utils.LocationProvider
import com.paul.weatpaper.utils.WallpaperChanger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.paul.weatpaper.BuildConfig

class WallpaperWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    private val locationProvider = LocationProvider(context)
    private val wallpaperChanger = WallpaperChanger(context)
    private val sharedPreferences =
        context.getSharedPreferences("weatpaper_prefs", Context.MODE_PRIVATE)
    private val apiKey = BuildConfig.OPENWEATHER_API_KEY

    override fun doWork(): Result {
        Log.d("WallpaperWorker", "OpenWeather API Key: $apiKey")

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            if (!isWorkEligible()) {
                return@launch
            }
            locationProvider.getLastLocation(
                onSuccess = { location ->
                    if (location != null) {
                        fetchWeatherAndChangeWallpaper(location.latitude, location.longitude)
                    } else {
                        Log.e("WallpaperWorker", "Location is null")
                        showToast("Error: Unable to retrieve location")
                    }
                },
                onFailure = { exception ->
                    Log.e("WallpaperWorker", "Location error: ${exception.message}")
                    showToast("Error: ${exception.message}")
                }
            )
        }
        return Result.success()
    }

    private suspend fun isWorkEligible(): Boolean {
        val lastExecutionTime = sharedPreferences.getLong("lastExecutionTime", 0L)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastExecutionTime < 60 * 60 * 1000) {
            Log.d("WallpaperWorker", "Work called too soon, continuing work")
            return false
        }
        return true
    }

    private fun fetchWeatherAndChangeWallpaper(latitude: Double, longitude: Double) {
        val weatherApi = ApiClient.getWeatherApi()
        weatherApi.getWeather(latitude, longitude, apiKey)
            .enqueue(object : retrofit2.Callback<JsonObject> {
                override fun onResponse(
                    call: retrofit2.Call<JsonObject>,
                    response: retrofit2.Response<JsonObject>
                ) {
                    if (response.isSuccessful) {
                        val weatherCondition = response.body()?.getAsJsonArray("weather")
                            ?.get(0)?.asJsonObject?.get("main")?.asString
                        if (weatherCondition != null) {
                            wallpaperChanger.changeWallpaper(weatherCondition)
                        } else {
                            Log.e("WallpaperWorker", "Weather condition not found in response")
                            showToast("Error: Unable to fetch weather condition")
                        }
                    } else {
                        Log.e("WallpaperWorker", "API Error: ${response.code()} - ${response.message()}")
                        showToast("Error: Unable to fetch weather data (${response.code()})")
                    }
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) {
                    Log.e("WallpaperWorker", "Network Error: ${t.message}")
                    showToast("Error: Network issue while fetching weather data")
                    t.printStackTrace()
                }
            })
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
