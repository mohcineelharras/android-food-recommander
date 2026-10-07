package com.foodrecommender.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ProductionGuardsTest {

    @Test
    fun coordinatesAndRadiusRejectUnsafeValues() {
        assertThrows(IllegalArgumentException::class.java) { LatLng(Double.NaN, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { LatLng(Double.POSITIVE_INFINITY, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { LatLng(91.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { LatLng(0.0, 181.0) }
        assertThrows(IllegalArgumentException::class.java) { sanitizeRadiusMeters(99) }
        assertThrows(IllegalArgumentException::class.java) { sanitizeRadiusMeters(50_001) }
    }

    @Test
    fun overpassQueryUsesCoarseNumbersOnly() {
        val query = buildRestaurantOverpassQuery(LatLng(12.341, 77.349), 1500)
        assertEquals(
            "[out:json][timeout:25];\n" +
                "node[\"amenity\"~\"^(restaurant|cafe|fast_food|bakery)$\"](around:1500,12.34,77.35);\n" +
                "out body 40;",
            query,
        )
        assertFalse(query.contains("12.341"))
        assertFalse(query.contains(";out"))
        assertEquals("12.34,77.35,1500", cacheKey(LatLng(12.341, 77.349), 1500))
    }

    @Test
    fun searchSendsCoarseCoordinatesAndHidesUpstreamErrors() = runBlocking {
        var seen: LatLng? = null
        val coordinator = coordinator(
            google = { location, _ ->
                seen = location
                throw IllegalStateException("key=AIzaSECRET")
            },
            overpass = { _, _ -> listOf(sample()) },
        )
        val results = coordinator.search(LatLng(12.341, 77.349), 1000)
        assertEquals(12.34, seen!!.latitude, 0.0)
        assertEquals(77.35, seen!!.longitude, 0.0)
        assertEquals("Noodle Bar", results.single().name)
    }

    @Test
    fun failedSearchDoesNotLeakSecretsOrFillTheCache() = runBlocking {
        var puts = 0
        val coordinator = coordinator(
            google = { _, _ -> throw IllegalStateException("key=AIzaSECRET") },
            overpass = { _, _ -> throw IllegalStateException("host=169.254.169.254") },
            cachePut = { _, _ -> puts += 1 },
        )
        val error = assertThrows(SearchException::class.java) {
            runBlocking { coordinator.search(LatLng(12.34, 77.35), 1000) }
        }
        assertEquals(UserMessages.PLACES, error.message)
        assertNull(error.cause)
        assertFalse(error.message!!.contains("AIza"))
        assertFalse(error.message!!.contains("169.254"))
        assertEquals(0, puts)
    }

    @Test
    fun cancellationDoesNotFallThroughToTheNextApi() {
        runBlocking {
            val coordinator = coordinator(
                google = { _, _ -> throw CancellationException("stop") },
                overpass = { _, _ -> error("overpass should not run") },
            )
            assertThrows(CancellationException::class.java) {
                runBlocking { coordinator.search(LatLng(12.34, 77.35), 1000) }
            }
        }
    }

    @Test
    fun missingGoogleKeyUsesOverpassWithoutOpeningTheBreaker() = runBlocking {
        var calls = 0
        val coordinator = coordinator(
            googleConfigured = true,
            google = { _, _ ->
                calls += 1
                throw PlacesNotConfiguredException()
            },
            overpass = { _, _ -> listOf(sample()) },
        )
        repeat(4) { index -> coordinator.search(LatLng(12.0 + index, 77.0), 1000) }
        assertEquals(4, calls)
    }

    @Test
    fun unconfiguredGoogleIsNotCalled() = runBlocking {
        var calls = 0
        val coordinator = coordinator(
            googleConfigured = false,
            google = { _, _ ->
                calls += 1
                emptyList()
            },
            overpass = { _, _ -> listOf(sample()) },
        )
        coordinator.search(LatLng(12.34, 77.35), 1000)
        assertEquals(0, calls)
    }

    @Test
    fun repeatedGoogleFailuresOpenTheCircuit() = runBlocking {
        var calls = 0
        var clock = 1_000L
        val breaker = ApiCircuitBreaker(failureThreshold = 2, resetTimeoutMillis = 500, now = { clock })
        val coordinator = coordinator(
            google = { _, _ ->
                calls += 1
                throw IllegalStateException("down")
            },
            overpass = { _, _ -> listOf(sample("fallback")) },
            circuitBreaker = breaker,
        )
        coordinator.search(LatLng(10.0, 20.0), 1000)
        coordinator.search(LatLng(11.0, 21.0), 1000)
        coordinator.search(LatLng(12.0, 22.0), 1000)
        assertEquals(2, calls)
        assertTrue(breaker.shouldBlock("google"))
        clock = 1_600
        assertFalse(breaker.shouldBlock("google"))
    }

    @Test
    fun sameCoarseAreaHitsTheCacheOnce() = runBlocking {
        var calls = 0
        val cache = mutableMapOf<String, List<Place>>()
        val coordinator = coordinator(
            google = { _, _ ->
                calls += 1
                listOf(sample())
            },
            cacheGet = { id -> cache[id] },
            cachePut = { id, places -> cache[id] = places },
        )
        coordinator.search(LatLng(12.341, 77.34), 1000)
        coordinator.search(LatLng(12.344, 77.344), 1000)
        assertEquals(1, calls)
        assertEquals(listOf("12.34,77.34,1000"), cache.keys.toList())
    }

    @Test
    fun parsersDropContactDetailsSecretsAndMarkup() {
        val overpass = parseOverpassPlaces(
            """
            {"elements":[{"id":42,"lat":12.341111,"lon":77.1,"tags":{
              "name":"<script>alert(1)</script>Safe Bites",
              "phone":"+91 99999",
              "email":"chef@example.com",
              "website":"https://example.com"
            }}]}
            """.trimIndent(),
        )
        assertEquals("42", overpass.single().id)
        assertEquals("alert(1)Safe Bites", overpass.single().name)
        assertFalse(overpass.single().name.contains("<"))

        val google = parseGooglePlaces(
            """
            {"places":[{"id":"p1","displayName":{"text":"Noodle Bar"},"rating":4.5,
              "userRatingCount":12,"priceLevel":"PRICE_LEVEL_MODERATE",
              "nationalPhoneNumber":"555-0100",
              "location":{"latitude":12.341234,"longitude":77.123456},
              "apiKey":"AIzaSECRET"}]}
            """.trimIndent(),
        )
        val encoded = placesToJson(google)
        assertEquals(2, google.single().priceLevel)
        assertEquals(4.5, google.single().rating!!, 0.0)
        assertFalse(encoded.contains("AIzaSECRET"))
        assertFalse(encoded.contains("555-0100"))
        assertFalse(encoded.contains("12.341"))
        assertFalse(encoded.contains("99999"))
        assertFalse(encoded.contains("chef@"))
        assertFalse(placesToJson(overpass).contains("12.341111"))
    }

    @Test
    fun oversizedAndBrokenPayloadsStayGeneric() {
        val oversized = assertThrows(IllegalArgumentException::class.java) {
            parseOverpassPlaces("x".repeat(MAX_PLACES_BODY_CHARS + 1))
        }
        assertEquals(UserMessages.RESPONSE_TOO_LARGE, oversized.message)
        assertThrows(Exception::class.java) { parseGooglePlaces("{") }
        val streamError = assertThrows(IllegalArgumentException::class.java) {
            readLimitedUtf8(ByteArrayInputStream("hello!".toByteArray()), 5)
        }
        assertEquals(UserMessages.RESPONSE_TOO_LARGE, streamError.message)
        assertEquals("hello", readLimitedUtf8(ByteArrayInputStream("hello".toByteArray()), 5))
    }

    @Test
    fun failureMessagesNeverEchoTheOriginalException() {
        val leaked = IllegalStateException("token=super-secret")
        assertEquals(UserMessages.PLACES, messageForFailure(leaked))
        assertFalse(messageForFailure(leaked).contains("super-secret"))
        assertEquals(UserMessages.LOCATION_PERMISSION, messageForFailure(LocationDeniedException()))
        assertEquals(UserMessages.LOCATION_UNAVAILABLE, messageForFailure(LocationUnavailableException()))
    }

    @Test
    fun apiKeyAndHostChecksRejectInjection() {
        assertTrue(isSafeApiKey("AIzaSyA_key-1"))
        assertFalse(isSafeApiKey(""))
        assertFalse(isSafeApiKey("abc\r\nX-Injected: 1"))
        assertFalse(isSafeApiKey("key with space"))
        assertTrue(PlacesEndpoints.isAllowedHost("places.googleapis.com"))
        assertTrue(PlacesEndpoints.isAllowedHost("overpass-api.de"))
        assertFalse(PlacesEndpoints.isAllowedHost("places.googleapis.com.evil.com"))
        assertFalse(PlacesEndpoints.isAllowedHost("localhost"))
        assertFalse(PlacesEndpoints.isAllowedHost(""))
    }

    @Test
    fun memoryCacheExpiresWithoutKeepingADiskCopy() {
        var now = 0L
        val cache = MemoryPlaceCache(ttlMillis = 1_000, now = { now })
        cache.put("12.34,77.35,1000", listOf(sample()))
        assertEquals("Noodle Bar", cache.get("12.34,77.35,1000")!!.single().name)
        now = 1_001
        assertNull(cache.get("12.34,77.35,1000"))
        cache.put("area", listOf(sample()))
        cache.clear()
        assertNull(cache.get("area"))
    }

    @Test
    fun localSummaryDoesNotNeedReviewText() {
        val analyzed = analyzePlaces(
            listOf(
                Place(id = "2", name = "Quiet Cafe"),
                Place(id = "1", name = "Noodle Bar", rating = 4.5, reviewCount = 1),
            ),
        )
        assertEquals("1", analyzed[0].place.id)
        assertEquals("Rated 4.5 from 1 review", analyzed[0].summary)
        assertEquals("No public rating yet", analyzed[1].summary)
    }

    private fun sample(id: String = "1") = Place(
        id = id,
        name = "Noodle Bar",
        rating = 4.5,
        reviewCount = 8,
        priceLevel = 2,
    )

    private fun coordinator(
        googleConfigured: Boolean = true,
        google: suspend (LatLng, Int) -> List<Place> = { _, _ -> listOf(sample()) },
        overpass: suspend (LatLng, Int) -> List<Place> = { _, _ -> emptyList() },
        cacheGet: suspend (String) -> List<Place>? = { null },
        cachePut: suspend (String, List<Place>) -> Unit = { _, _ -> },
        circuitBreaker: ApiCircuitBreaker = ApiCircuitBreaker(),
    ) = NearbySearchCoordinator(
        googleConfigured = googleConfigured,
        google = google,
        overpass = overpass,
        cacheGet = cacheGet,
        cachePut = cachePut,
        circuitBreaker = circuitBreaker,
    )
}
