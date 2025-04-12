package com.paul.weatpaper.ui

import android.Manifest // Upewnij się, że Manifest jest zaimportowany
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // <<< Dodany import dla AlertDialog
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
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
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
        // Przy starcie sprawdzamy, czy uprawnienie jest JUŻ nadane (bez uruchamiania usługi)
        checkLocationPermissions(false) // Sprawdzi stan i nic nie uruchomi jeśli są nadane
        observeWorkerState()
    }

    private fun setupButtonsClickListeners() {
        findViewById<Button>(R.id.howitwork_button).setOnClickListener { startActivity(Intent(this, HowItWorksActivity::class.java)) }
        findViewById<Button>(R.id.info_button).setOnClickListener { startActivity(Intent(this, InfoActivity::class.java)) }
        findViewById<Button>(R.id.button_website).setOnClickListener { openWebsite() }
        startButton.setOnClickListener { startWallpaperServiceWithChecks() }
        stopButton.setOnClickListener { stopWallpaperWorker() }
    }

    private fun observeWorkerState() {
        workManager.getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
            .observe(this, Observer { workInfos ->
                // ... (logika obserwera bez zmian dotyczących lokalizacji) ...
                var isWorkerActive = false
                var workerStatusText = "Stan usługi: Zatrzymana"

                if (workInfos != null && workInfos.isNotEmpty()) {
                    val workInfo = workInfos[0]
                    val currentState = workInfo.state
                    isWorkerActive = currentState == WorkInfo.State.ENQUEUED || currentState == WorkInfo.State.RUNNING

                    workerStatusText = when (currentState) {
                        WorkInfo.State.ENQUEUED -> "Stan usługi: Oczekuje w kolejce"
                        WorkInfo.State.RUNNING -> "Stan usługi: Uruchomiona (pracuje)"
                        WorkInfo.State.SUCCEEDED -> "Stan usługi: Zakończona (czeka na cykl)"
                        WorkInfo.State.FAILED -> "Stan usługi: Błąd (spróbuje ponownie)"
                        WorkInfo.State.BLOCKED -> "Stan usługi: Zablokowana (czeka na warunki)"
                        WorkInfo.State.CANCELLED -> "Stan usługi: Anulowana"
                        else -> "Stan usługi: Nieznany ($currentState)"
                    }

                    Log.d(TAG, "Worker state observed: $currentState, Active: $isWorkerActive")

                    val progressData = workInfo.progress
                    val progress = progressData.getInt(WallpaperWorker.PROGRESS_KEY, -1)

                    if (progress >= 0 && (currentState == WorkInfo.State.RUNNING || currentState == WorkInfo.State.ENQUEUED)) {
                        workerProgressBar.visibility = View.VISIBLE
                        workerProgressBar.progress = progress
                        Log.d(TAG, "Worker progress: $progress%")
                        if (currentState == WorkInfo.State.RUNNING && progress == 0) {
                            workerStatusText += " (rozpoczynanie...)"
                        } else if (currentState == WorkInfo.State.RUNNING && progress == 100) {
                            workerStatusText += " (kończenie...)"
                        }

                    } else {
                        workerProgressBar.visibility = View.GONE
                        workerProgressBar.progress = 0
                        if(currentState == WorkInfo.State.FAILED){
                            // Dodatkowe sprawdzenie, czy błąd był spowodowany brakiem uprawnień
                            // (Można by to przekazać przez dane wyjściowe Workera, ale na razie zostawiamy ogólny błąd)
                            if (progress == -1) { // Możemy użyć -1 jako kodu błędu, jak w workerze
                                workerStatusText = "Stan usługi: Błąd (np. lokalizacji, API)"
                            } else {
                                workerStatusText = "Stan usługi: Błąd (spróbuje ponownie)"
                            }
                        }
                    }

                } else {
                    Log.d(TAG, "No WorkInfo found for $UNIQUE_WORK_NAME. Worker is inactive.")
                    workerStatusText = "Stan usługi: Zatrzymana"
                    workerProgressBar.visibility = View.GONE
                    workerProgressBar.progress = 0
                }
                updateButtonStates(isWorkerActive)
                workerStatusTextView.text = workerStatusText
            })
    }

    private fun updateButtonStates(isWorkerActive: Boolean) {
        // ... (bez zmian) ...
        startButton.isEnabled = !isWorkerActive
        stopButton.isEnabled = isWorkerActive
        startButton.alpha = if (startButton.isEnabled) 1.0f else 0.5f
        stopButton.alpha = if (stopButton.isEnabled) 1.0f else 0.5f
        Log.d(TAG, "Buttons updated: Start enabled=${startButton.isEnabled}, Stop enabled=${stopButton.isEnabled}")
    }

    private fun openWebsite() {
        // ... (bez zmian) ...
        val url = if (Locale.getDefault().language == "pl") "https://weatpaper.pl/polityka-prywatnosci" else "https://weatpaper.com/privacy-policy"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open website: ${e.message}")
            Toast.makeText(this, "Nie można otworzyć strony", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startWallpaperServiceWithChecks() {
        if (isInternetAvailable()) {
            // Sprawdź uprawnienia i jeśli są ok, uruchom usługę
            // Jeśli nie są ok, funkcja checkLocationPermissions pokaże dialog
            checkLocationPermissions(true)
        } else {
            Toast.makeText(this, "Brak połączenia z internetem", Toast.LENGTH_LONG).show()
        }
    }

    // === ZMODYFIKOWANA FUNKCJA ===
    // Sprawdza uprawnienia. Jeśli nie ma, POKAZUJE DIALOG Z WYJAŚNIENIEM przed prośbą systemową.
    private fun checkLocationPermissions(startServiceOnGrant: Boolean) {
        when {
            // Uprawnienie już jest nadane
            areLocationPermissionsGranted() -> {
                if (startServiceOnGrant) {
                    startWallpaperService() // Uruchom usługę, jeśli to było celem
                } else {
                    Log.d(TAG, "Approximate location permission already granted.")
                }
            }
            // Uprawnienie NIE jest nadane - POKAŻ DIALOG Z WYJAŚNIENIEM
            else -> {
                // Wywołaj funkcję pokazującą dialog ZANIM poprosisz o uprawnienie
                showBackgroundLocationDisclosureDialog()
            }
        }
    }

    // === NOWA FUNKCJA: Pokazuje dialog wyjaśniający potrzebę lokalizacji w tle ===
    private fun showBackgroundLocationDisclosureDialog() {
        AlertDialog.Builder(this)
            .setTitle("Wymagana lokalizacja w tle") // Tytuł okna
            .setMessage(
                "Aplikacja Weatpaper potrzebuje dostępu do Twojej lokalizacji w tle.\n\n" +
                        "Pozwoli to na automatyczne aktualizowanie tapety zgodnie z aktualną pogodą w Twojej okolicy, nawet gdy aplikacja nie jest aktywna lub ekran jest wyłączony.\n\n" +
                        "Twoja przybliżona lokalizacja będzie okresowo sprawdzana w tym celu."
            ) // Wyjaśnienie
            .setPositiveButton("Rozumiem i kontynuuj") { dialog, _ ->
                // Użytkownik zaakceptował wyjaśnienie, teraz poproś o uprawnienie systemowe
                requestLocationPermissions()
                dialog.dismiss()
            }
            .setNegativeButton("Anuluj") { dialog, _ ->
                // Użytkownik anulował, nie proś o uprawnienie
                Toast.makeText(this, "Bez zgody na lokalizację, automatyczna aktualizacja tapety nie będzie możliwa.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setCancelable(false) // Opcjonalnie: uniemożliwia zamknięcie dialogu przez kliknięcie poza nim
            .show()
    }


    // === Funkcja prosi system o nadanie uprawnień (bez zmian) ===
    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
        Log.d(TAG, "Requesting COARSE location permission.")
    }

    // Sprawdza, czy uprawnienie lokalizacji jest nadane (bez zmian)
    private fun areLocationPermissionsGranted(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Checking COARSE location permission: $granted")
        return granted
    }

    // Uruchamia WorkManagera (bez zmian)
    private fun startWallpaperService() {
        scheduleWallpaperWorker()
        Log.i(TAG,"Wallpaper service start requested (scheduling periodic work)")
    }

    // Planuje zadanie WorkManagera (bez zmian)
    private fun scheduleWallpaperWorker() {
        val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
            3, // Ustaw interwał (np. 3 godziny)
            TimeUnit.HOURS
        )
            .addTag("WallpaperWorkerTag")
            .build()

        try {
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE, // REPLACE: Anuluje poprzednie zadanie i tworzy nowe
                workRequest
            )
            Log.d(TAG, "PeriodicWorkRequest enqueued with REPLACE policy for $UNIQUE_WORK_NAME")
            Toast.makeText(this, "Usługa tapet została zaplanowana.", Toast.LENGTH_SHORT).show()
            // Obserwator WorkManagera powinien automatycznie zaktualizować stan przycisków

        } catch (e: IllegalStateException) {
            // To może się zdarzyć, jeśli WorkManager nie jest gotowy (np. podczas szybkiego przełączania)
            Log.e(TAG, "Failed to schedule Periodic WallpaperWorker due to IllegalStateException: ${e.message}")
            Toast.makeText(this, "Błąd podczas planowania usługi (WorkManager): Spróbuj ponownie za chwilę.", Toast.LENGTH_LONG).show()
        }
        catch (e: Exception) {
            Log.e(TAG, "Failed to schedule Periodic WallpaperWorker: ${e.message}")
            Toast.makeText(this, "Błąd podczas planowania usługi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Zatrzymuje WorkManagera (bez zmian)
    private fun stopWallpaperWorker() {
        try {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG,"Stopped unique work: $UNIQUE_WORK_NAME")
            Toast.makeText(this, "Usługa tapet została zatrzymana.", Toast.LENGTH_SHORT).show()
            // Obserwator WorkManagera powinien automatycznie zaktualizować stan przycisków
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop WallpaperWorker: ${e.message}")
            Toast.makeText(this, "Błąd podczas zatrzymywania usługi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Sprawdza dostęp do internetu (bez zmian)
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Obsługuje wynik prośby o uprawnienia (bez zmian)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // Sprawdźmy, czy to na pewno odpowiedź na naszą prośbę (powinna zawierać COARSE)
            if (permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "ACCESS_COARSE_LOCATION permission granted.")
                    // Uprawnienie nadane po pokazaniu dialogu, teraz można uruchomić usługę
                    startWallpaperService()
                } else {
                    Log.w(TAG, "ACCESS_COARSE_LOCATION permission denied.")
                    Toast.makeText(this, "Odmówiono uprawnień lokalizacji (przybliżonej).", Toast.LENGTH_SHORT).show()
                    // Sprawdź, czy użytkownik zaznaczył "Nie pytaj ponownie"
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        // Użytkownik zaznaczył "Nie pytaj ponownie" LUB odmówił na stałe w ustawieniach
                        Toast.makeText(this, "Włącz uprawnienia lokalizacji (przybliżone) ręcznie w ustawieniach aplikacji, aby korzystać z tej funkcji.", Toast.LENGTH_LONG).show()
                        // Opcjonalnie: Przenieś do ustawień aplikacji
                        try {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            })
                        } catch (e: Exception){
                            Log.e(TAG, "Could not open app settings: ${e.message}")
                            Toast.makeText(this, "Nie można otworzyć ustawień aplikacji.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}