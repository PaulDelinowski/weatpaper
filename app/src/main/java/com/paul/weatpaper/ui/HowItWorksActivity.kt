package com.paul.weatpaper.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.paul.weatpaper.R

class HowItWorksActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false) // Zamiast enableEdgeToEdge()
        setContentView(R.layout.activity_how_it_works)

        // Ustawienie paddingu dla systemowych pasków
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.how_it_works_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Znalezienie przycisku i ustawienie nasłuchiwacza kliknięć
        val backButton: AppCompatButton = findViewById(R.id.button_back)
        backButton.setOnClickListener {
            finish() // Zakończenie aktywności i powrót do poprzedniej
        }
    }
}