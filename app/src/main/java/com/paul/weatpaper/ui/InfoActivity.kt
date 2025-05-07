package com.paul.weatpaper.ui

// Upewnij się, że masz te importy (lub dodaj brakujące)
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log // Dodaj Log dla debugowania
import android.widget.Button
import android.widget.Toast // Dodaj Toast dla obsługi błędów
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.paul.weatpaper.R
import java.util.Locale // Dodaj Locale do sprawdzania języka

class InfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)
        enableEdgeToEdge()

        // Znalezienie przycisków
        val backButton: Button = findViewById(R.id.button_back)
        val helpButton: Button = findViewById(R.id.button_help) // Znajdź przycisk pomocy

        // Ustawienie słuchacza kliknięcia dla przycisku powrotu (istniejący kod)
        backButton.setOnClickListener {
            finish() // Zakończ aktualną aktywność, aby wrócić do MainActivity
        }

        // <<< POCZĄTEK NOWEGO KODU DLA BUTTON_HELP >>>
        helpButton.setOnClickListener {
            // Sprawdź język urządzenia
            val currentLanguage = Locale.getDefault().language
            val urlToOpen = if (currentLanguage == "pl") {
                "https://youtube.com/shorts/2gG03obVkgM" // URL dla języka polskiego
            } else {
                "https://youtube.com/shorts/g9-7pjrmAjQ" // URL dla innych języków
            }

            Log.d("InfoActivity", "Help button clicked. Language: $currentLanguage, URL: $urlToOpen") // Log dla debugowania

            // Spróbuj otworzyć URL
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                startActivity(intent)
            } catch (e: Exception) {
                // Obsługa błędu, np. gdy nie ma przeglądarki
                Log.e("InfoActivity", "Failed to open URL '$urlToOpen': ${e.message}")
                Toast.makeText(this, "Nie można otworzyć linku.", Toast.LENGTH_SHORT).show()
            }
        }
        // <<< KONIEC NOWEGO KODU DLA BUTTON_HELP >>>


        // Istniejący kod dla WindowInsets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun enableEdgeToEdge() {
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}