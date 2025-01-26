package com.paul.weatpaper.utils

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class WallpaperChanger(private val context: Context) {

    fun changeWallpaper(weatherCondition: String, sunrise: Long, sunset: Long) {
        val currentTime = System.currentTimeMillis() / 1000 // Aktualny czas w sekundach

        val partOfDay = when (currentTime) {
            in sunrise until sunset -> "day"
            in (sunset + 1)..(sunset + 3 * 60 * 60) -> "sunset" // 3 godziny po zachodzie
            in (sunrise - 3 * 60 * 60) until sunrise -> "sunrise" // 3 godziny przed wschodem
            else -> "night"
        }

        val weatherFolder = when (weatherCondition.lowercase(Locale.ROOT)) {
            "clear" -> "clear"
            "clouds" -> "cloudy"
            "rain" -> "rainy"
            "thunderstorm" -> "storm"
            "drizzle" -> "rainy"
            "snow" -> "snowy"
            else -> "default"
        }

        val folderPath = "$weatherFolder/$partOfDay"
        Log.d("WallpaperChanger", "Loading wallpapers from: $folderPath")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assetManager = context.assets
                val wallpapers = assetManager.list(folderPath) ?: emptyArray()
                Log.d("WallpaperChanger", "Available wallpapers: ${wallpapers.joinToString(", ")}")

                if (wallpapers.isNotEmpty()) {
                    val randomWallpaper = wallpapers.random()
                    assetManager.open("$folderPath/$randomWallpaper").use { inputStream ->
                        WallpaperManager.getInstance(context).setStream(inputStream)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Wallpaper changed to $randomWallpaper", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No wallpapers found for $folderPath", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Log.e("WallpaperChanger", "Error changing wallpaper", e)
                    Toast.makeText(context, "Failed to change wallpaper", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
