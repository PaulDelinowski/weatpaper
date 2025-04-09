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
import androidx.lifecycle.Observer // Potrzebny import dla Observera LiveData
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo // Potrzebny import dla WorkInfo.State
import androidx.work.WorkManager
import com.paul.weatpaper.R
import com.paul.weatpaper.worker.WallpaperWorker
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val TAG = "MainActivity"
        private const val UNIQUE_WORK_NAME = "WallpaperWorker"
    }

    // Dodaj zmienne dla przycisków
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var workManager: WorkManager // Instancja WorkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate called")

        // Zainicjuj WorkManager
        workManager = WorkManager.getInstance(this)

        // Zainicjuj przyciski (znajdź je po ID)
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)
        // Możesz też zainicjować pozostałe przyciski, jeśli potrzebujesz do nich dostępu

        setupButtonsClickListeners() // Zmieniona nazwa funkcji ustawiającej listenery
        checkLocationPermissions(false)

        // Obserwuj stan workera, aby aktualizować przyciski
        observeWorkerState()
    }

    // Funkcja ustawiająca tylko listenery
    private fun setupButtonsClickListeners() {
        findViewById<Button>(R.id.howitwork_button).setOnClickListener { startActivity(Intent(this, HowItWorksActivity::class.java)) }
        findViewById<Button>(R.id.info_button).setOnClickListener { startActivity(Intent(this, InfoActivity::class.java)) }
        findViewById<Button>(R.id.button_website).setOnClickListener { openWebsite() }
        startButton.setOnClickListener { startWallpaperServiceWithChecks() }
        stopButton.setOnClickListener { stopWallpaperWorker() }
    }

    // Funkcja do obserwowania stanu workera
    private fun observeWorkerState() {
        workManager.getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
            .observe(this, Observer { workInfos ->
                var isWorkerActive = false // Domyślnie worker nie jest aktywny
                if (workInfos != null && workInfos.isNotEmpty()) {
                    // Sprawdź stan pierwszego (i zazwyczaj jedynego) WorkInfo dla unikalnej pracy
                    val currentState = workInfos[0].state
                    // Uważamy workera za aktywnego, jeśli jest w kolejce lub działa
                    isWorkerActive = currentState == WorkInfo.State.ENQUEUED || currentState == WorkInfo.State.RUNNING
                    Log.d(TAG, "Worker state observed: $currentState, Active: $isWorkerActive")
                } else {
                    Log.d(TAG, "No WorkInfo found for $UNIQUE_WORK_NAME. Worker is inactive.")
                }
                // Zaktualizuj stan przycisków na podstawie aktywności workera
                updateButtonStates(isWorkerActive)
            })
    }

    // Funkcja do aktualizowania stanu przycisków Start/Stop
    private fun updateButtonStates(isWorkerActive: Boolean) {
        startButton.isEnabled = !isWorkerActive // Włącz Start, jeśli worker NIE jest aktywny
        stopButton.isEnabled = isWorkerActive  // Włącz Stop, jeśli worker JEST aktywny

        // Opcjonalnie: zmień wygląd przycisków (np. przezroczystość), gdy są wyłączone
        startButton.alpha = if (startButton.isEnabled) 1.0f else 0.5f
        stopButton.alpha = if (stopButton.isEnabled) 1.0f else 0.5f
        Log.d(TAG, "Buttons updated: Start enabled=${startButton.isEnabled}, Stop enabled=${stopButton.isEnabled}")
    }


    // --- Reszta kodu pozostaje taka sama jak w poprzedniej wersji ---
    // --- (openWebsite, startWallpaperServiceWithChecks, checkLocationPermissions, ---
    // --- requestLocationPermissions, areLocationPermissionsGranted, startWallpaperService, ---
    // --- scheduleWallpaperWorker z KEEP, stopWallpaperWorker, isInternetAvailable, ---
    // --- onRequestPermissionsResult) ---

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
        // Toast nie jest już tak potrzebny, bo stan przycisków się zaktualizuje
        // Toast.makeText(this, "Wallpaper service scheduled (Periodic with KEEP)", Toast.LENGTH_SHORT).show()
        Log.i(TAG,"Wallpaper service start requested (scheduling periodic work with KEEP)")
        // Uwaga: LiveData może nie zaktualizować się natychmiast po enqueue,
        // więc przyciski mogą przez chwilę pozostać w starym stanie.
        // Można by *optymistycznie* wyłączyć Start od razu tutaj, ale obserwacja LiveData jest pewniejsza.
    }

    private fun scheduleWallpaperWorker() {
        val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
            1,
            TimeUnit.HOURS
        )
            .addTag("WallpaperWorkerTag")
            .build()

        try {
            workManager.enqueueUniquePeriodicWork( // Używamy zmiennej workManager
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // *** Polityka KEEP ***
                workRequest
            )
            Log.d(TAG, "PeriodicWorkRequest enqueued with KEEP policy for $UNIQUE_WORK_NAME")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule Periodic WallpaperWorker: ${e.message}")
        }
    }

    private fun stopWallpaperWorker() {
        try {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME) // Używamy zmiennej workManager
            // Toast.makeText(this, "WallpaperWorker stopped", Toast.LENGTH_SHORT).show()
            Log.i(TAG,"Stopped unique work: $UNIQUE_WORK_NAME")
            // Stan przycisków zaktualizuje się automatycznie przez LiveData observer
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