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
import java.util.Calendar
import java.util.Locale

class WallpaperChanger(private val context: Context) {

    fun changeWallpaper(weatherCondition: String) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val partOfDay = when (hourOfDay) {
            in 5..8 -> "sunrise"
            in 9..16 -> "day"
            in 17..20 -> "sunset"
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
                        wallpaperManager.setStream(inputStream)
                    }
                    withContext(Dispatchers.Main) {
                        Log.d("WallpaperChanger", "Wallpaper changed to $randomWallpaper")
                        Toast.makeText(context, "Wallpaper changed successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.d("WallpaperChanger", "No wallpapers found in folder: $folderPath")
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
