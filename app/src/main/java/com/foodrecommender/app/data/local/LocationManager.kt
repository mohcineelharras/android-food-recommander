package com.foodrecommender.app.data.local

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.foodrecommender.core.LatLng
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * One-shot approximate location. Coordinates are coarsened before they leave this class
 * and are not written to disk.
 */
class LocationManager(context: Context) {

    private val appContext = context.applicationContext
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }
    private val lock = Any()
    private var requestGeneration = 0
    private var cancellation: CancellationTokenSource? = null

    sealed class LocationResult {
        data class Success(val location: LatLng) : LocationResult()
        data object PermissionDenied : LocationResult()
        data object Unavailable : LocationResult()
    }

    fun hasCoarsePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun requestCoarseArea(callback: (LocationResult) -> Unit) {
        if (!hasCoarsePermission()) {
            callback(LocationResult.PermissionDenied)
            return
        }
        val generation: Int
        val tokenSource: CancellationTokenSource
        synchronized(lock) {
            requestGeneration += 1
            generation = requestGeneration
            cancellation?.cancel()
            tokenSource = CancellationTokenSource()
            cancellation = tokenSource
        }
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                tokenSource.token,
            ).addOnSuccessListener { location ->
                if (location == null) {
                    deliver(generation, LocationResult.Unavailable, callback)
                    return@addOnSuccessListener
                }
                val coarse = try {
                    LatLng(location.latitude, location.longitude).coarsen()
                } catch (_: IllegalArgumentException) {
                    deliver(generation, LocationResult.Unavailable, callback)
                    return@addOnSuccessListener
                }
                deliver(generation, LocationResult.Success(coarse), callback)
            }.addOnFailureListener {
                deliver(generation, LocationResult.Unavailable, callback)
            }
        } catch (_: SecurityException) {
            callback(LocationResult.PermissionDenied)
        }
    }

    fun stop() {
        synchronized(lock) {
            requestGeneration += 1
            cancellation?.cancel()
            cancellation = null
        }
    }

    private fun deliver(generation: Int, result: LocationResult, callback: (LocationResult) -> Unit) {
        synchronized(lock) {
            if (requestGeneration != generation) return
            requestGeneration += 1
            cancellation = null
        }
        callback(result)
    }
}
