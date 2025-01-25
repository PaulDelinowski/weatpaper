package com.paul.weatpaper.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.paul.weatpaper.R
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate called")
        setupButtons()
        checkLocationPermissions(false)
    }

    private fun setupButtons() {
        val buttonsMap = mapOf<Button, () -> Unit>(
            findViewById<Button>(R.id.howitwork_button) to { startActivity(Intent(this, HowItWorksActivity::class.java)) },
            findViewById<Button>(R.id.info_button) to { startActivity(Intent(this, InfoActivity::class.java)) },
            findViewById<Button>(R.id.button_website) to { openWebsite() },
            findViewById<Button>(R.id.start_button) to { startWallpaperServiceWithChecks() },
            findViewById<Button>(R.id.stop_button) to { stopWallpaperWorker() }
        )

        buttonsMap.forEach { (button, action) ->
            button.setOnClickListener { action() }
        }
    }

    private fun openWebsite() {
        val url = if (Locale.getDefault().language == "pl") "https://weatpaper.pl" else "https://weatpaper.com"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open website: ${e.message}")
            Toast.makeText(this, "Unable to open website", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startWallpaperServiceWithChecks() {
        if (isInternetAvailable()) {
            checkLocationPermissions(true)
        } else {
            Toast.makeText(this, "No internet connection available", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkLocationPermissions(startServiceOnGrant: Boolean) {
        when {
            areLocationPermissionsGranted() -> {
                if (startServiceOnGrant) startWallpaperService()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Toast.makeText(this, "Location permission is needed for wallpaper updates", Toast.LENGTH_LONG).show()
                requestLocationPermissions()
            }
            else -> requestLocationPermissions()
        }
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun areLocationPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startWallpaperService() {
        scheduleWallpaperWorker()
        Toast.makeText(this, "Wallpaper service started", Toast.LENGTH_SHORT).show()
    }

    private fun scheduleWallpaperWorker() {
        val workRequest = PeriodicWorkRequestBuilder<com.paul.weatpaper.worker.WallpaperWorker>(
            1,
            TimeUnit.HOURS
        )
            .addTag("WallpaperWorkerTag")
            .build()

        try {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "WallpaperWorker",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "PeriodicWorkRequest enqueued with 1 hour interval")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule WallpaperWorker: ${e.message}")
        }
    }

    private fun stopWallpaperWorker() {
        try {
            WorkManager.getInstance(this).cancelUniqueWork("WallpaperWorker")
            Toast.makeText(this, "WallpaperWorker stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop WallpaperWorker: ${e.message}")
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startWallpaperService()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Toast.makeText(this, "Please enable location permission in settings", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                }
            }
        }
    }
}
