package com.paul.weatpaper.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
// import android.os.Build // Już niepotrzebny
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Observer
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.paul.weatpaper.R
import com.paul.weatpaper.worker.WallpaperWorker
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        // === ZOSTAJE TYLKO KOD DLA COARSE ===
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        // private const val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 2 // Usunięte
        // ===================================
        private const val TAG = "MainActivity"
        private const val UNIQUE_WORK_NAME = "WallpaperWorker"
    }

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var workManager: WorkManager
    private lateinit var workerStatusTextView: TextView
    private lateinit var workerProgressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate called")

        workManager = WorkManager.getInstance(this)
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)
        workerStatusTextView = findViewById(R.id.worker_status_textview)
        workerProgressBar = findViewById(R.id.worker_progress_bar)

        setupButtonsClickListeners()
        observeWorkerState()
    }

    private fun setupButtonsClickListeners() {
        findViewById<Button>(R.id.howitwork_button).setOnClickListener { startActivity(Intent(this, HowItWorksActivity::class.java)) }
        findViewById<Button>(R.id.info_button).setOnClickListener { startActivity(Intent(this, InfoActivity::class.java)) }
        findViewById<Button>(R.id.button_website).setOnClickListener { openWebsite() }
        startButton.setOnClickListener { startWallpaperServiceWithChecks() }
        stopButton.setOnClickListener { stopWallpaperWorker() }

        // <<< TUTAJ DODAJ OBSŁUGĘ NOWEGO PRZYCISKU >>>
        findViewById<Button>(R.id.button_updates).setOnClickListener {
            // Sprawdź język urządzenia
            val currentLanguage = Locale.getDefault().language
            val url = if (currentLanguage == "pl") {
                "https://weatpaper.pl/news" // URL dla języka polskiego
            } else {
                "https://weatpaper.com/news" // Domyślny URL (angielski)
            }

            // Spróbuj otworzyć URL w przeglądarce
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                Log.d(TAG, "Opening URL for updates: $url") // Opcjonalny log
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open updates URL '$url': ${e.message}") // Opcjonalny log błędu
                // Poinformuj użytkownika o problemie, jeśli chcesz
                Toast.makeText(this, "Cannot open news page.", Toast.LENGTH_SHORT).show()
            }
        }
        // <<< KONIEC DODANEJ OBSŁUGI >>>
    }
    // Funkcja observeWorkerState pozostaje bez zmian
    private fun observeWorkerState() {
        workManager.getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
            .observe(this, Observer { workInfos ->
                var isWorkerActive = false
                var workerStatusText = "Service Status: Stopped"

                if (workInfos != null && workInfos.isNotEmpty()) {
                    val workInfo = workInfos[0]
                    val currentState = workInfo.state
                    isWorkerActive = currentState == WorkInfo.State.ENQUEUED || currentState == WorkInfo.State.RUNNING

                    workerStatusText = when (currentState) {
                        WorkInfo.State.ENQUEUED -> "Service Status: Enqueued"
                        WorkInfo.State.RUNNING -> "Service Status: Running"
                        WorkInfo.State.SUCCEEDED -> "Service Status: Succeeded (waiting for cycle)"
                        WorkInfo.State.FAILED -> "Service Status: Failed (will retry)"
                        WorkInfo.State.BLOCKED -> "Service Status: Blocked (waiting for constraints)"
                        WorkInfo.State.CANCELLED -> "Service Status: Cancelled"
                        else -> "Service Status: Unknown ($currentState)"
                    }

                    Log.d(TAG, "Worker state observed: $currentState, Active: $isWorkerActive")

                    val progressData = workInfo.progress
                    val progress = progressData.getInt(WallpaperWorker.PROGRESS_KEY, -1)

                    if (progress >= 0 && (currentState == WorkInfo.State.RUNNING || currentState == WorkInfo.State.ENQUEUED)) {
                        workerProgressBar.visibility = View.VISIBLE
                        workerProgressBar.progress = progress
                        Log.d(TAG, "Worker progress: $progress%")
                        if (currentState == WorkInfo.State.RUNNING && progress == 0) {
                            workerStatusText += " (starting...)"
                        } else if (currentState == WorkInfo.State.RUNNING && progress == 100) {
                            workerStatusText += " (finishing...)"
                        }
                    } else {
                        workerProgressBar.visibility = View.GONE
                        workerProgressBar.progress = 0
                        if(currentState == WorkInfo.State.FAILED){
                            // Sprawdzanie błędu pozostaje bez zmian
                            val errorMessage = workInfo.outputData.getString(WallpaperWorker.ERROR_MESSAGE_KEY) // Użycie stałej
                            workerStatusText = if (!errorMessage.isNullOrEmpty()) {
                                "Service Status: Error ($errorMessage)"
                            } else {
                                "Service Status: Error (e.g., location, API)"
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No WorkInfo found for $UNIQUE_WORK_NAME. Worker is inactive.")
                    workerProgressBar.visibility = View.GONE
                    workerProgressBar.progress = 0
                }
                updateButtonStates(isWorkerActive)
                workerStatusTextView.text = workerStatusText
            })
    }

    // Funkcja updateButtonStates pozostaje bez zmian
    private fun updateButtonStates(isWorkerActive: Boolean) {
        startButton.isEnabled = !isWorkerActive
        stopButton.isEnabled = isWorkerActive
        startButton.alpha = if (startButton.isEnabled) 1.0f else 0.5f
        stopButton.alpha = if (stopButton.isEnabled) 1.0f else 0.5f
        Log.d(TAG, "Buttons updated: Start enabled=${startButton.isEnabled}, Stop enabled=${stopButton.isEnabled}")
    }

    // Funkcja openWebsite pozostaje bez zmian
    private fun openWebsite() {
        val url = if (Locale.getDefault().language == "pl") "https://weatpaper.pl/polityka-prywatnosci" else "https://weatpaper.com/privacy-policy"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open website: ${e.message}")
            Toast.makeText(this, "The page cannot be opened", Toast.LENGTH_SHORT).show()
        }
    }

    // Funkcja startWallpaperServiceWithChecks pozostaje bez zmian
    private fun startWallpaperServiceWithChecks() {
        if (isInternetAvailable()) {
            checkLocationPermissions(true)
        } else {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show()
        }
    }

    // === UPROSZCZONA FUNKCJA SPRAWDZANIA UPRAWNIEŃ ===
    private fun checkLocationPermissions(startServiceOnGrant: Boolean) {
        // Sprawdzamy tylko uprawnienie COARSE
        if (isCoarseLocationGranted()) {
            Log.d(TAG, "ACCESS_COARSE_LOCATION already granted.")
            // Jeśli jest zgoda, można startować
            if (startServiceOnGrant) {
                startWallpaperService()
            }
        } else {
            // COARSE brakuje - poproś o nie
            Log.d(TAG, "ACCESS_COARSE_LOCATION needs to be requested.")
            // Pokaż wyjaśnienie i poproś o COARSE
            showLocationDisclosureDialog() // Wywołanie bez parametru
        }
    }

    // === UPROSZCZONA FUNKCJA POKAZUJĄCA DIALOG ===
    private fun showLocationDisclosureDialog() {
        val title = "Wymagana lokalizacja"
        val message = "The Weatpaper app needs access to your approximate location\n\n" +
                "This is needed to download weather data for your area and set the appropriate wallpaper." // Tylko wyjaśnienie dla COARSE
        val positiveButtonText = "Rozumiem i kontynuuj"
        val negativeButtonText = "Anuluj"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { dialog, _ ->
                dialog.dismiss()
                // Po akceptacji wyjaśnienia, poproś o COARSE
                requestCoarseLocationPermission()
            }
            .setNegativeButton(negativeButtonText) { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Without location permission, the app cannot download weather and set wallpaper.", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    // === FUNKCJE POMOCNICZE - ZOSTAJĄ TYLKO TE DLA COARSE ===

    private fun isCoarseLocationGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Funkcja isBackgroundLocationGranted() - USUNIĘTA

    private fun requestCoarseLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE // Używamy kodu dla COARSE
        )
        Log.d(TAG, "Requesting COARSE location permission.")
    }

    // Funkcja requestBackgroundLocationPermission() - USUNIĘTA

    // === UPROSZCZONA FUNKCJA OBSŁUGI WYNIKÓW ===
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode, permissions=${permissions.joinToString()}, grantResults=${grantResults.joinToString()}")

        // Sprawdzamy tylko odpowiedź na żądanie COARSE_LOCATION
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "ACCESS_COARSE_LOCATION permission granted.")
                // Sukces, można uruchomić usługę
                startWallpaperService()
            } else {
                Log.w(TAG, "ACCESS_COARSE_LOCATION permission denied.")
                Toast.makeText(this, "Location permission (approximate) denied. Feature unavailable.", Toast.LENGTH_SHORT).show()
                handlePermissionDeniedPermanently(Manifest.permission.ACCESS_COARSE_LOCATION, "przybliżoną")
            }
        }
        // Usunięto `when` i case dla BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
    }

    // Funkcje handlePermissionDeniedPermanently i showSettingsRedirectDialog pozostają bez zmian,
    // będą teraz wywoływane tylko dla odmowy ACCESS_COARSE_LOCATION
    private fun handlePermissionDeniedPermanently(permission: String, permissionNameFriendly: String) {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            Log.w(TAG, "Permission '$permission' denied permanently or policy restricted.")
            showSettingsRedirectDialog(permissionNameFriendly)
        }
    }

    private fun showSettingsRedirectDialog(permissionNameFriendly: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission required")
            .setMessage("For this feature to work properly, location consent is required ($permissionNameFriendly). It has been permanently rejected. Would you like to go to the app settings to enable it manually?")
            .setPositiveButton("Go to settings") { dialog, _ ->
                dialog.dismiss()
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open app settings: ${e.message}")
                    Toast.makeText(this, "Cannot open application settings.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Anuluj") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Without the required permissions, the feature will not work.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }


    // === Funkcje związane z Workerem (scheduleWallpaperWorker, stopWallpaperWorker, isInternetAvailable) pozostają bez zmian ===
    private fun startWallpaperService() {
        scheduleWallpaperWorker()
        Log.i(TAG,"Wallpaper service start requested (scheduling periodic work)")
    }

    private fun scheduleWallpaperWorker() {
        // Zmień interwał zgodnie z potrzebami, minimum 15 minut
        val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
            30, TimeUnit.MINUTES // Przykład: 15 minut
        )
            .addTag("WallpaperWorkerTag")
            // Możesz dodać ograniczenia, np. wymagać połączenia z siecią
            // .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        try {
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "PeriodicWorkRequest enqueued with REPLACE policy for $UNIQUE_WORK_NAME")
            Toast.makeText(this, getString(R.string.wallpaper_service_enabled), Toast.LENGTH_SHORT).show()

        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to schedule Periodic WallpaperWorker due to IllegalStateException: ${e.message}")
            Toast.makeText(this, "Error scheduling service (WorkManager): Please try again in a moment.", Toast.LENGTH_LONG).show()
        }
        catch (e: Exception) {
            Log.e(TAG, "Failed to schedule Periodic WallpaperWorker: ${e.message}", e)
            Toast.makeText(this, "Error while scheduling service: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopWallpaperWorker() {
        try {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG,"Stopped unique work: $UNIQUE_WORK_NAME")
            Toast.makeText(this, "Automatic wallpaper change has been disabled.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop WallpaperWorker: ${e.message}")
            Toast.makeText(this, "Error stopping service: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}