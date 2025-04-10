package com.paul.weatpaper.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class LocationProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocationProvider" // Zachowujemy TAG
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getLastLocationSuspend(): Location? = suspendCancellableCoroutine { continuation ->
        // Sprawdzenie uprawnień jest teraz oparte o zmodyfikowaną arePermissionsGranted()
        if (!arePermissionsGranted()) {
            Log.e(TAG, "COARSE Location permission not granted before calling getLastLocationSuspend.")
            continuation.resumeWithException(
                SecurityException("Approximate location permission is required.")
            )
            return@suspendCancellableCoroutine
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                Log.d(TAG, "getLastLocationSuspend successful. Location: ${location?.latitude}, ${location?.longitude} (Accuracy may be coarse)")
                continuation.resume(location)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "getLastLocationSuspend failed.", exception)
                continuation.resumeWithException(exception)
            }

        continuation.invokeOnCancellation {
            Log.d(TAG, "getLastLocationSuspend coroutine cancelled.")
        }
    }

    // Funkcja getCurrentLocationSuspend pozostaje bez zmian funkcjonalnych,
    // ale również będzie ograniczona, jeśli tylko COARSE jest przyznane.
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationSuspend(): Location? = suspendCancellableCoroutine { continuation ->
        if (!arePermissionsGranted()) { // Używa tej samej funkcji sprawdzającej
            Log.e(TAG, "COARSE Location permission not granted before calling getCurrentLocationSuspend.")
            continuation.resumeWithException(
                SecurityException("Approximate location permission is required.")
            )
            return@suspendCancellableCoroutine
        }
        // ... reszta getCurrentLocationSuspend bez zmian ...
        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token
        )
            .addOnSuccessListener { location: Location? ->
                Log.d(TAG, "getCurrentLocationSuspend successful. Location: ${location?.latitude}, ${location?.longitude} (Accuracy may be coarse)")
                continuation.resume(location)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "getCurrentLocationSuspend failed.", exception)
                continuation.resumeWithException(exception)
            }
        continuation.invokeOnCancellation {
            cancellationTokenSource.cancel()
            Log.d(TAG, "getCurrentLocationSuspend coroutine cancelled, location task cancelled.")
        }
    }

    // Sprawdza, czy nadano uprawnienie ACCESS_COARSE_LOCATION
    private fun arePermissionsGranted(): Boolean {
        // --- ZMIANA: Sprawdzaj tylko COARSE ---
        val coarseLocationGranted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "Permissions Check: Coarse=$coarseLocationGranted (Only Coarse requested/checked)")

        return coarseLocationGranted
        // =====================================
    }
}