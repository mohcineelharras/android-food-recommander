package com.foodrecommender.app.data.remote

import com.foodrecommender.core.LatLng
import com.foodrecommender.core.MAX_PLACES_BODY_CHARS
import com.foodrecommender.core.Place
import com.foodrecommender.core.PlacesEndpoints
import com.foodrecommender.core.PlacesNotConfiguredException
import com.foodrecommender.core.buildRestaurantOverpassQuery
import com.foodrecommender.core.isSafeApiKey
import com.foodrecommender.core.parseGooglePlaces
import com.foodrecommender.core.parseOverpassPlaces
import com.foodrecommender.core.readLimitedUtf8
import com.foodrecommender.core.sanitizeRadiusMeters
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class PlacesHttp(apiKey: String) {
    private val key = apiKey.trim()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(AllowedHostInterceptor())
        .addNetworkInterceptor(ApiKeyScopeInterceptor())
        .addInterceptor(MinIntervalInterceptor(1_000))
        .build()

    suspend fun searchGoogle(location: LatLng, radiusMeters: Int): List<Place> = withContext(Dispatchers.IO) {
        if (!isSafeApiKey(key)) throw PlacesNotConfiguredException()
        val coarse = location.coarsen()
        val radius = sanitizeRadiusMeters(radiusMeters)
        val bodyJson = JSONObject()
            .put("includedTypes", JSONArray(listOf("restaurant", "cafe", "bakery")))
            .put("maxResultCount", 20)
            .put(
                "locationRestriction",
                JSONObject().put(
                    "circle",
                    JSONObject()
                        .put(
                            "center",
                            JSONObject()
                                .put("latitude", coarse.latitude)
                                .put("longitude", coarse.longitude),
                        )
                        .put("radius", radius.toDouble()),
                ),
            )
        val request = Request.Builder()
            .url(PlacesEndpoints.GOOGLE_URL)
            .header("X-Goog-Api-Key", key)
            .header(
                "X-Goog-FieldMask",
                "places.id,places.displayName,places.rating,places.userRatingCount,places.priceLevel",
            )
            .post(bodyJson.toString().toRequestBody(JSON))
            .build()
        execute(request, ::parseGooglePlaces)
    }

    suspend fun searchOverpass(location: LatLng, radiusMeters: Int): List<Place> = withContext(Dispatchers.IO) {
        val query = buildRestaurantOverpassQuery(location, radiusMeters)
        val request = Request.Builder()
            .url(PlacesEndpoints.OVERPASS_URL)
            .header("User-Agent", "FoodRecommender/1.0")
            .post(query.toRequestBody(TEXT))
            .build()
        execute(request, ::parseOverpassPlaces)
    }

    private fun execute(request: Request, parse: (String) -> List<Place>): List<Place> {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("places request failed")
            val payload = response.body ?: throw IOException("places request failed")
            val text = readLimitedUtf8(payload.byteStream(), MAX_PLACES_BODY_CHARS)
            return parse(text)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val TEXT = "text/plain; charset=utf-8".toMediaType()
    }
}

class ApiKeyScopeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!PlacesEndpoints.isAllowedHost(request.url.host)) {
            throw IOException("blocked host")
        }
        val guarded = if (request.url.host == PlacesEndpoints.GOOGLE_HOST) {
            request
        } else {
            request.newBuilder().removeHeader("X-Goog-Api-Key").build()
        }
        return chain.proceed(guarded)
    }
}

class AllowedHostInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (!PlacesEndpoints.isAllowedHost(host)) {
            throw IOException("blocked host")
        }
        return chain.proceed(chain.request())
    }
}

class MinIntervalInterceptor(private val intervalMs: Long) : Interceptor {
    private val lock = Any()
    private var lastCallMs = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val wait = intervalMs - (now - lastCallMs)
            if (wait > 0) {
                try {
                    Thread.sleep(wait)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("places request failed")
                }
            }
            lastCallMs = System.currentTimeMillis()
        }
        return chain.proceed(chain.request())
    }
}
