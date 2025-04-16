package com.paul.weatpaper

import android.app.Application
import android.util.Log // Dodaj import dla Log, jeśli go używasz

// Importy WorkManager i Configuration nie są już potrzebne tutaj
// import androidx.work.Configuration
// import androidx.work.WorkManager

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Celowo usunięto ręczną inicjalizację WorkManagera.
        // Usunięcie poniższego bloku naprawia błąd IllegalStateException.

        /*
        WorkManager.initialize(
            this,
            Configuration.Builder().build()
        )
        */

        // Tutaj możesz dodać inną logikę inicjalizacyjną
        Log.i("MyApp", "Application onCreate finished.") // Przykładowy log
    }
}