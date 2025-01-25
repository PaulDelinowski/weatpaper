package com.paul.weatpaper

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize WorkManager with default configuration
        WorkManager.initialize(
            this,
            Configuration.Builder().build()
        )
    }
}