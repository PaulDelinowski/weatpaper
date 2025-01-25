package com.paul.weatpaper.ui

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.paul.weatpaper.R

class InfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)
        enableEdgeToEdge()

        // Ustawienie słuchacza kliknięcia dla przycisku powrotu
        val backButton: Button = findViewById(R.id.button_back)
        backButton.setOnClickListener {
            finish() // Zakończ aktualną aktywność, aby wrócić do MainActivity
        }

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
