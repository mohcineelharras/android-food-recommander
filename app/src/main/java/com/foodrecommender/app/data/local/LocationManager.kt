package com.foodrecommender.app.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*
import kotlin.math.pow

class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    private var locationCallback: LocationCallback? = null
    private var timeoutHandler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val maxRetries = 5

    sealed class LocationResult {
        data class Success(val latitude: Double, val longitude: Double) : LocationResult()
        data class Error(val message: String) : LocationResult()
        object PermissionDenied : LocationResult()
        object Timeout : LocationResult()
    }

    interface LocationCallback {
        fun onLocationStateChanged(result: LocationResult)
    }

    fun requestLocationUpdates(radiusKm: Int, callback: LocationCallback) {
        if (!hasLocationPermission()) {
            callback.onLocationStateChanged(LocationResult.PermissionDenied)
            return
        }

        startLocationRequests(radiusKm, callback)
    }

    private fun startLocationRequests(radiusKm: Int, callback: LocationCallback) {
        val radiusMeters = convertKmToMeters(radiusKm)
        val locationRequest = createLocationRequest(radiusMeters)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                locationResult.lastLocation?.let {
                    saveLastLocation(it.latitude, it.longitude)
                    callback.onLocationStateChanged(LocationResult.Success(it.latitude, it.longitude))
                    cleanup()
                } ?: handleRetry(radiusKm, callback)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    handleRetry(radiusKm, callback)
                }
            }
        }

        startLocationUpdatesWithBackoff(locationRequest, callback)
        scheduleTimeout(callback)
    }

    private fun createLocationRequest(radiusMeters: Int): LocationRequest {
        return LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = Priority.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = radiusMeters.toFloat()
            maxWaitTime = 30000
        }
    }

    private fun startLocationUpdatesWithBackoff(request: LocationRequest, callback: LocationCallback) {
        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback as LocationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            callback.onLocationStateChanged(LocationResult.Error("Security Exception: ${e.message}"))
        }
    }

    private fun handleRetry(radiusKm: Int, callback: LocationCallback) {
        if (retryCount < maxRetries) {
            val delay = (2.0.pow(retryCount.toDouble()) * 1000).toLong()
            Handler(Looper.getMainLooper()).postDelayed({
                retryCount++
                startLocationRequests(radiusKm, callback)
            }, delay)
        } else {
            callback.onLocationStateChanged(LocationResult.Error("Max retries reached"))
            cleanup()
        }
    }

    private fun scheduleTimeout(callback: LocationCallback) {
        timeoutHandler.postDelayed({
            callback.onLocationStateChanged(LocationResult.Timeout)
            cleanup()
        }, 30000)
    }

    fun handlePermissionResult(granted: Boolean, callback: LocationCallback) {
        if (granted) {
            retryCount = 0
            requestLocationUpdates(getLastRadius(), callback)
        } else {
            callback.onLocationStateChanged(LocationResult.PermissionDenied)
        }
    }

    private fun cleanup() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        timeoutHandler.removeCallbacksAndMessages(null)
        retryCount = 0
    }

    // Helper functions
    fun convertKmToMeters(km: Int) = km * 1000
    
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun saveLastLocation(lat: Double, lng: Double) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(LAST_LOCATION_KEY, "$lat,$lng")
            .apply()
    }

    fun getLastLocation(): Pair<Double, Double>? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val locationStr = prefs.getString(LAST_LOCATION_KEY, null)
        return locationStr?.split(",")?.let {
            Pair(it[0].toDouble(), it[1].toDouble())
        }
    }

    fun getLastRadius(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getInt(LAST_RADIUS_KEY, 1)
    }

    companion object {
        private const val LAST_LOCATION_KEY = "last_known_location"
        private const val LAST_RADIUS_KEY = "last_used_radius"
        
        // For testing mock location
        fun setMockLocation(context: Context, lat: Double, lng: Double) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(LAST_LOCATION_KEY, "$lat,$lng")
                .apply()
        }
    }
}
