package com.paul.weatpaper.utils

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Calendar

class WallpaperChanger(private val context: Context) {

    fun changeWallpaper(weatherCondition: String) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val partOfDay = when (hourOfDay) {
            in 7..9 -> "sunrise"
            in 10..15 -> "day"
            in 18..17 -> "sunset"
            else -> "night"
        }

        val weatherFolder = when (weatherCondition) {
            "clear" -> "clear"
            "partly_cloudy" -> "partly_cloudy"
            "mostly_cloudy" -> "mostly_cloudy"
            "cloudy" -> "cloudy"
            "rain" -> "rainy"
            else -> "default"
        }

        val folderPath = "$weatherFolder/$partOfDay"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assetManager = context.assets
                val wallpapers = assetManager.list(folderPath) ?: emptyArray()

                if (wallpapers.isNotEmpty()) {
                    val randomWallpaper = wallpapers.random()
                    val inputStream = assetManager.open("$folderPath/$randomWallpaper")
                    wallpaperManager.setStream(inputStream)
                    withContext(Dispatchers.Main) {
                        Log.d("WallpaperChanger", "Wallpaper changed successfully to $randomWallpaper")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.d("WallpaperChanger", "No wallpapers found in folder: $folderPath")
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Log.e("WallpaperChanger", "Error changing wallpaper", e)
                }
            }
        }
    }
}
