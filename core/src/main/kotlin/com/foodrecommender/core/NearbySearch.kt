package com.foodrecommender.core

import kotlinx.coroutines.CancellationException

const val CACHE_TTL_MS = 60L * 60L * 1000L

class ApiCircuitBreaker(
    private val failureThreshold: Int = 3,
    private val resetTimeoutMillis: Long = 60_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val failureCounts = mutableMapOf<String, Int>()
    private val openUntil = mutableMapOf<String, Long>()

    fun recordFailure(api: String) {
        synchronized(lock) {
            val count = (failureCounts[api] ?: 0) + 1
            failureCounts[api] = count
            if (count >= failureThreshold) {
                openUntil[api] = now() + resetTimeoutMillis
            }
        }
    }

    fun shouldBlock(api: String): Boolean {
        synchronized(lock) {
            val until = openUntil[api] ?: return false
            if (now() < until) return true
            openUntil.remove(api)
            failureCounts.remove(api)
            return false
        }
    }
}

interface CoarseLocationSource {
    suspend fun current(): LatLng
}

interface PlacesRepository {
    suspend fun getNearbyPlaces(location: LatLng, radiusMeters: Int): List<Place>
}

class NearbySearchCoordinator(
    private val googleConfigured: Boolean,
    private val google: suspend (LatLng, Int) -> List<Place>,
    private val overpass: suspend (LatLng, Int) -> List<Place>,
    private val cacheGet: suspend (String) -> List<Place>?,
    private val cachePut: suspend (String, List<Place>) -> Unit,
    private val circuitBreaker: ApiCircuitBreaker = ApiCircuitBreaker(),
) {
    suspend fun search(location: LatLng, radiusMeters: Int): List<Place> {
        val radius = sanitizeRadiusMeters(radiusMeters)
        val key = cacheKey(location, radius)
        cacheGet(key)?.let { return it }
        val places = fetch(location.coarsen(), radius)
        cachePut(key, places)
        return places
    }

    private suspend fun fetch(location: LatLng, radius: Int): List<Place> {
        if (!googleConfigured || circuitBreaker.shouldBlock(GOOGLE)) {
            return overpassOrThrow(location, radius)
        }
        return try {
            google(location, radius)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (notConfigured: PlacesNotConfiguredException) {
            overpassOrThrow(location, radius)
        } catch (failure: Exception) {
            circuitBreaker.recordFailure(GOOGLE)
            overpassOrThrow(location, radius)
        }
    }

    private suspend fun overpassOrThrow(location: LatLng, radius: Int): List<Place> {
        try {
            return overpass(location, radius)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw SearchException()
        }
    }

    private companion object {
        const val GOOGLE = "google"
    }
}
