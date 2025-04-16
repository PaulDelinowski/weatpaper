package com.paul.weatpaper.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build // <<< Dodany import dla Build
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
        // === ZMIENIONE/DODANE KODY ŻĄDAŃ ===
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1 // Dla COARSE
        private const val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 2 // Dla BACKGROUND
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
        // Przy starcie sprawdzamy, czy uprawnienia są JUŻ nadane (bez uruchamiania usługi)
        // checkLocationPermissions(false) // Można usunąć lub zostawić, jeśli chcesz sprawdzić stan od razu
        observeWorkerState() // Obserwator i tak pokaże aktualny stan workera
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
                // --- ZMIANA: Teksty po angielsku ---
                var workerStatusText = "Service Status: Stopped" // Domyślnie

                if (workInfos != null && workInfos.isNotEmpty()) {
                    val workInfo = workInfos[0]
                    val currentState = workInfo.state
                    isWorkerActive = currentState == WorkInfo.State.ENQUEUED || currentState == WorkInfo.State.RUNNING

                    workerStatusText = when (currentState) {
                        WorkInfo.State.ENQUEUED -> "Service Status: Enqueued"
                        WorkInfo.State.RUNNING -> "Service Status: Running"
                        WorkInfo.State.SUCCEEDED -> "Service Status: Succeeded (waiting for cycle)"
                        WorkInfo.State.FAILED -> "Service Status: Failed (will retry)" // Domyślnie dla FAILED
                        WorkInfo.State.BLOCKED -> "Service Status: Blocked (waiting for constraints)"
                        WorkInfo.State.CANCELLED -> "Service Status: Cancelled"
                        else -> "Service Status: Unknown ($currentState)" // Uwzględnienie stanu nieznanego
                    }
                    // --- Koniec ZMIANY ---

                    Log.d(TAG, "Worker state observed: $currentState, Active: $isWorkerActive")

                    val progressData = workInfo.progress
                    val progress = progressData.getInt(WallpaperWorker.PROGRESS_KEY, -1)

                    if (progress >= 0 && (currentState == WorkInfo.State.RUNNING || currentState == WorkInfo.State.ENQUEUED)) {
                        workerProgressBar.visibility = View.VISIBLE
                        workerProgressBar.progress = progress
                        Log.d(TAG, "Worker progress: $progress%")
                        // --- ZMIANA: Teksty po angielsku ---
                        if (currentState == WorkInfo.State.RUNNING && progress == 0) {
                            workerStatusText += " (starting...)"
                        } else if (currentState == WorkInfo.State.RUNNING && progress == 100) {
                            workerStatusText += " (finishing...)"
                        }
                        // --- Koniec ZMIANY ---

                    } else {
                        workerProgressBar.visibility = View.GONE
                        workerProgressBar.progress = 0
                        if(currentState == WorkInfo.State.FAILED){
                            if (progress == -1) { // Możemy użyć -1 jako kodu błędu
                                // --- ZMIANA: Teksty po angielsku ---
                                // Sprawdźmy, czy w workData jest wiadomość o błędzie
                                val errorMessage = workInfo.outputData.getString("error_message")
                                workerStatusText = if (!errorMessage.isNullOrEmpty()) {
                                    "Service Status: Error ($errorMessage)"
                                } else {
                                    "Service Status: Error (e.g., location, API)"
                                }
                                // --- Koniec ZMIANY ---
                            }
                            // else: workerStatusText pozostaje domyślnym dla FAILED (Failed (will retry))
                        }
                    }

                } else {
                    Log.d(TAG, "No WorkInfo found for $UNIQUE_WORK_NAME. Worker is inactive.")
                    // --- ZMIANA: Teksty po angielsku (już ustawione domyślnie wyżej) ---
                    // workerStatusText = "Service Status: Stopped" // Niepotrzebne, bo to domyślne
                    // --- Koniec ZMIANY ---
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

    // === GŁÓWNA FUNKCJA SPRAWDZAJĄCA ===
    private fun startWallpaperServiceWithChecks() {
        if (isInternetAvailable()) {
            // Rozpocznij proces sprawdzania uprawnień
            checkLocationPermissions(true)
        } else {
            Toast.makeText(this, "Brak połączenia z internetem", Toast.LENGTH_LONG).show()
        }
    }

    // === ZMODYFIKOWANA FUNKCJA SPRAWDZANIA UPRAWNIEŃ ===
    private fun checkLocationPermissions(startServiceOnGrant: Boolean) {
        // 1. Sprawdź uprawnienie pierwszoplanowe (COARSE)
        if (isCoarseLocationGranted()) {
            Log.d(TAG, "ACCESS_COARSE_LOCATION already granted.")
            // 2. Jeśli COARSE jest, sprawdź uprawnienie TŁA (tylko na Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isBackgroundLocationGranted()) {
                    Log.d(TAG, "ACCESS_BACKGROUND_LOCATION already granted.")
                    // Wszystkie potrzebne uprawnienia są, można startować
                    if (startServiceOnGrant) {
                        startWallpaperService()
                    }
                } else {
                    // COARSE jest, ale BACKGROUND brakuje (na Android 10+)
                    Log.d(TAG, "ACCESS_BACKGROUND_LOCATION needs to be requested.")
                    // Pokaż wyjaśnienie i poproś o BACKGROUND
                    showLocationDisclosureDialog(isRequestingBackground = true)
                }
            } else {
                // Na Androidzie < 10, COARSE wystarcza
                Log.d(TAG, "Running on Android < Q, BACKGROUND location permission not required.")
                if (startServiceOnGrant) {
                    startWallpaperService()
                }
            }
        } else {
            // COARSE brakuje - poproś najpierw o nie
            Log.d(TAG, "ACCESS_COARSE_LOCATION needs to be requested.")
            // Pokaż wyjaśnienie i poproś o COARSE
            showLocationDisclosureDialog(isRequestingBackground = false)
        }
    }

    // === NOWA, UNIWERSALNA FUNKCJA POKAZUJĄCA DIALOG ===
    // Decyduje, o które uprawnienie poprosić po zamknięciu dialogu
    private fun showLocationDisclosureDialog(isRequestingBackground: Boolean) {
        val title: String
        val message: String
        val positiveButtonText = "Rozumiem i kontynuuj"
        val negativeButtonText = "Anuluj"

        if (isRequestingBackground) {
            title = "Wymagana lokalizacja w tle"
            message = "Aplikacja Weatpaper potrzebuje dostępu do Twojej lokalizacji **w tle**.\n\n" +
                    "Pozwoli to na automatyczne aktualizowanie tapety zgodnie z pogodą w Twojej okolicy, nawet gdy aplikacja nie jest aktywna.\n\n" +
                    "Bez tej zgody tapeta będzie aktualizowana tylko, gdy aplikacja będzie otwarta." // Zaktualizowane wyjaśnienie
        } else {
            title = "Wymagana lokalizacja"
            message = "Aplikacja Weatpaper potrzebuje dostępu do Twojej **przybliżonej lokalizacji**.\n\n" +
                    "Jest to potrzebne do pobrania danych pogodowych dla Twojej okolicy i ustawienia odpowiedniej tapety.\n\n" +
                    "Najpierw wymagana jest zgoda na lokalizację, gdy aplikacja jest używana."
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { dialog, _ ->
                dialog.dismiss()
                // Po akceptacji wyjaśnienia, poproś o odpowiednie uprawnienie
                if (isRequestingBackground) {
                    requestBackgroundLocationPermission()
                } else {
                    requestCoarseLocationPermission()
                }
            }
            .setNegativeButton(negativeButtonText) { dialog, _ ->
                dialog.dismiss()
                val toastMessage = if (isRequestingBackground) {
                    "Bez zgody na lokalizację w tle, automatyczna aktualizacja tapety może nie działać poprawnie."
                } else {
                    "Bez zgody na lokalizację, aplikacja nie może pobrać pogody i ustawić tapety."
                }
                Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }


    // === FUNKCJE POMOCNICZE DO SPRAWDZANIA I ŻĄDANIA UPRAWNIEŃ ===

    private fun isCoarseLocationGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBackgroundLocationGranted(): Boolean {
        // Sprawdzaj tylko na Q+
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Na starszych wersjach nie ma tego uprawnienia, traktujemy jak przyznane
        }
    }

    private fun requestCoarseLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE // Używamy kodu dla COARSE
        )
        Log.d(TAG, "Requesting COARSE location permission.")
    }

    private fun requestBackgroundLocationPermission() {
        // Upewnij się, że to Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE // Używamy kodu dla BACKGROUND
            )
            Log.d(TAG, "Requesting BACKGROUND location permission.")
        }
    }

    // === ZAKTUALIZOWANA FUNKCJA OBSŁUGI WYNIKÓW ===
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode, permissions=${permissions.joinToString()}, grantResults=${grantResults.joinToString()}")

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                // Odpowiedź na żądanie COARSE_LOCATION
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "ACCESS_COARSE_LOCATION permission granted.")
                    // Sukces, teraz ponownie sprawdź stan (może trzeba poprosić o BACKGROUND)
                    checkLocationPermissions(true) // Ponowne sprawdzenie zainicjuje prośbę o background lub start usługi
                } else {
                    Log.w(TAG, "ACCESS_COARSE_LOCATION permission denied.")
                    Toast.makeText(this, "Odmówiono zgody na lokalizację (przybliżoną). Funkcja niedostępna.", Toast.LENGTH_SHORT).show()
                    handlePermissionDeniedPermanently(Manifest.permission.ACCESS_COARSE_LOCATION, "przybliżoną")
                }
            }
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                // Odpowiedź na żądanie BACKGROUND_LOCATION (tylko na Android 10+)
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "ACCESS_BACKGROUND_LOCATION permission granted.")
                    // Sukces, teraz można uruchomić usługę
                    startWallpaperService()
                } else {
                    Log.w(TAG, "ACCESS_BACKGROUND_LOCATION permission denied.")
                    Toast.makeText(this, "Odmówiono zgody na lokalizację w tle. Tapeta może nie aktualizować się automatycznie.", Toast.LENGTH_LONG).show()
                    handlePermissionDeniedPermanently(Manifest.permission.ACCESS_BACKGROUND_LOCATION, "w tle")
                }
            }
        }
    }

    // === Funkcja pomocnicza do obsługi "Nie pytaj ponownie" ===
    private fun handlePermissionDeniedPermanently(permission: String, permissionNameFriendly: String) {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            // Użytkownik zaznaczył "Nie pytaj ponownie" LUB polityka urządzenia blokuje
            Log.w(TAG, "Permission '$permission' denied permanently or policy restricted.")
            showSettingsRedirectDialog(permissionNameFriendly)
        }
    }

    // === Dialog przekierowujący do ustawień ===
    private fun showSettingsRedirectDialog(permissionNameFriendly: String) {
        AlertDialog.Builder(this)
            .setTitle("Wymagane uprawnienie")
            .setMessage("Aby funkcja działała poprawnie, wymagana jest zgoda na lokalizację ($permissionNameFriendly). Została ona trwale odrzucona. Czy chcesz przejść do ustawień aplikacji, aby ją włączyć ręcznie?")
            .setPositiveButton("Przejdź do ustawień") { dialog, _ ->
                dialog.dismiss()
                // Otwórz ustawienia aplikacji
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open app settings: ${e.message}")
                    Toast.makeText(this, "Nie można otworzyć ustawień aplikacji.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Anuluj") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Bez wymaganych uprawnień funkcja nie będzie działać.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }


    // === FUNKCJE ZWIĄZANE Z WORKEREM (bez zmian) ===

    private fun startWallpaperService() {
        scheduleWallpaperWorker()
        Log.i(TAG,"Wallpaper service start requested (scheduling periodic work)")
        // Toast.makeText(this, "Usługa tapet została zaplanowana.", Toast.LENGTH_SHORT).show() // Można przenieść niżej
    }

    private fun scheduleWallpaperWorker() {
        val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
            // 30, TimeUnit.MINUTES // Minimum to 15 minut dla PeriodicWorkRequest
            30, TimeUnit.MINUTES // Przykładowo co godzinę
        )
            .addTag("WallpaperWorkerTag")
            // Można dodać ograniczenia, np. wymagać połączenia z siecią
            // .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        try {
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE, // REPLACE: Anuluje poprzednie i tworzy nowe
                workRequest
            )
            Log.d(TAG, "PeriodicWorkRequest enqueued with REPLACE policy for $UNIQUE_WORK_NAME")
            Toast.makeText(this, "Automatyczna zmiana tapety została włączona.", Toast.LENGTH_SHORT).show() // Lepszy komunikat

        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to schedule Periodic WallpaperWorker due to IllegalStateException: ${e.message}")
            Toast.makeText(this, "Błąd podczas planowania usługi (WorkManager): Spróbuj ponownie za chwilę.", Toast.LENGTH_LONG).show()
        }
        catch (e: Exception) {
            Log.e(TAG, "Failed to schedule Periodic WallpaperWorker: ${e.message}", e)
            Toast.makeText(this, "Błąd podczas planowania usługi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopWallpaperWorker() {
        try {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG,"Stopped unique work: $UNIQUE_WORK_NAME")
            Toast.makeText(this, "Automatyczna zmiana tapety została wyłączona.", Toast.LENGTH_SHORT).show() // Lepszy komunikat
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop WallpaperWorker: ${e.message}", e)
            Toast.makeText(this, "Błąd podczas zatrzymywania usługi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Dla nowszych API
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        // Starsze API (można pominąć jeśli minSdk jest >= 23)
        /*
        else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
        */
    }
}