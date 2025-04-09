package com.paul.weatpaper.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class LocationProvider(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Twoja dotychczasowa metoda z callbackami
    fun getLastLocation(
        onSuccess: (Location?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (!arePermissionsGranted()) {
            onFailure(SecurityException("Location permissions not granted"))
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener(onSuccess)
            .addOnFailureListener(onFailure)
    }

    // *** NOWA – wersja suspend ***
    suspend fun getLastLocationSuspend(): Location? = suspendCancellableCoroutine { continuation ->
        // Najpierw sprawdzamy uprawnienia
        if (!arePermissionsGranted()) {
            continuation.resumeWithException(
                SecurityException("Location permissions not granted")
            )
            return@suspendCancellableCoroutine
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                continuation.resume(location)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    private fun arePermissionsGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}
