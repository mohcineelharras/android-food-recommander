package com.foodrecommender.app.data.local

import com.foodrecommender.core.CoarseLocationSource
import com.foodrecommender.core.LatLng
import com.foodrecommender.core.LocationDeniedException
import com.foodrecommender.core.LocationUnavailableException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class FusedCoarseLocationSource(
    private val locationManager: LocationManager,
) : CoarseLocationSource {
    override suspend fun current(): LatLng = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { locationManager.stop() }
        locationManager.requestCoarseArea { result ->
            if (!continuation.isActive) return@requestCoarseArea
            when (result) {
                is LocationManager.LocationResult.Success -> continuation.resume(result.location)
                LocationManager.LocationResult.PermissionDenied ->
                    continuation.resumeWithException(LocationDeniedException())
                LocationManager.LocationResult.Unavailable ->
                    continuation.resumeWithException(LocationUnavailableException())
            }
        }
    }
}
