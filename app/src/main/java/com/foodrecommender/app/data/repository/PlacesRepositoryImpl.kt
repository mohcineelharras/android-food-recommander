package com.foodrecommender.app.data.repository

import androidx.room.withTransaction
import com.foodrecommender.app.data.local.AppDatabase
import com.foodrecommender.app.data.local.dao.PlaceDao
import com.foodrecommender.app.data.remote.*
import com.foodrecommender.app.domain.model.*
import com.foodrecommender.app.domain.repository.PlacesRepository
import com.foodrecommender.app.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.HttpException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

class PlacesRepositoryImpl(
    private val googlePlacesService: GooglePlacesService,
    private val overpassService: OverpassService,
    private val twitterService: TwitterService,
    private val placeDao: PlaceDao,
    private val circuitBreaker: ApiCircuitBreaker
) : PlacesRepository {

    private val rateLimiter = RateLimiter(2, TimeUnit.SECONDS)
    private val spamPattern = Regex("(?i)free|money|win|cash|claim|click|http|https|www|♥|♡")

    override fun getNearbyPlaces(location: LatLng, radius: Int): Flow<Resource<List<Place>>> = flow {
        try {
            rateLimiter.acquire()
            
            // Check cache first
            val cached = placeDao.getCachedPlaces(location, radius)
                .filter { it.timestamp > System.currentTimeMillis() - 3600000 }
            
            cached?.let {
                emit(Resource.Success(it.places))
                return@flow
            }

            // Circuit breaker state check
            if (circuitBreaker.shouldBlock("google")) {
                emit(Resource.Loading("Using fallback API..."))
                fetchFromOverpass(location, radius).collect { emit(it) }
                return@flow
            }

            // Main Google Places API call
            val response = try {
                googlePlacesService.nearbySearch(
                    location = "${location.lat},${location.lng}",
                    radius = radius,
                    types = "restaurant|bakery",
                    fields = "name,rating,user_ratings_total,price_level"
                )
            } catch (e: Exception) {
                circuitBreaker.recordFailure("google")
                throw e
            }

            val places = response.results.map { apiPlace ->
                Place(
                    id = apiPlace.place_id,
                    name = apiPlace.name,
                    rating = apiPlace.rating,
                    reviewCount = apiPlace.user_ratings_total,
                    priceLevel = apiPlace.price_level,
                    reviews = fetchCombinedReviews(apiPlace.name, location)
                )
            }

            // Cache with timestamp
            placeDao.insertCachedPlaces(
                CachedPlaces(
                    location = location,
                    radius = radius,
                    places = places,
                    timestamp = System.currentTimeMillis()
                )
            )

            emit(Resource.Success(places))
        } catch (e: Exception) {
            emit(Resource.Error("Failed to fetch places: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchFromOverpass(location: LatLng, radius: Int): Flow<Resource<List<Place>>> = flow {
        try {
            val query = """
                [out:json];
                node["amenity"~"restaurant|bakery"]
                (around:${radius},${location.lat},${location.lng});
                out body;
            """.trimIndent()

            val response = overpassService.search(query)
            val places = response.elements.map { element ->
                Place(
                    id = element.id.toString(),
                    name = element.tags["name"] ?: "Unknown",
                    rating = null,
                    reviewCount = null,
                    priceLevel = null,
                    reviews = emptyList()
                )
            }
            
            emit(Resource.Success(places))
        } catch (e: Exception) {
            emit(Resource.Error("Fallback API failed: ${e.message}"))
        }
    }

    private suspend fun fetchCombinedReviews(name: String, location: LatLng): List<Review> {
        return coroutineScope {
            val tripAdvisor = async { scrapeTripAdvisor(name, location) }
            val twitter = async { fetchTwitterMentions(name, location) }
            
            listOf(
                tripAdvisor.await(),
                twitter.await()
            ).flatten()
             .filterNot { isSpamReview(it.content) }
             .map { applySentimentAnalysis(it) }
        }
    }

    private suspend fun scrapeTripAdvisor(name: String, location: LatLng): List<Review> {
        return try {
            val url = buildTripAdvisorUrl(name, location)
            val doc = Jsoup.connect(url).get()
            
            doc.select(".review-container").map { element ->
                Review(
                    source = "TripAdvisor",
                    content = element.select(".reviewText").text(),
                    rating = element.select(".ui_bubble_rating").attr("class")
                        .substringAfterLast("_").toDouble() / 10,
                    timestamp = element.select(".ratingDate").attr("title")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildTripAdvisorUrl(name: String, location: LatLng): String {
        return "https://www.tripadvisor.com/Search?q=${name.replace(" ", "+")}" +
            "&geo=${location.lat.round(2)},${location.lng.round(2)}"
    }

    private suspend fun fetchTwitterMentions(name: String, location: LatLng): List<Review> {
        return try {
            val query = "($name OR #${name.replace(" ", "")}) (food OR meal) place_country:IN"
            val response = twitterService.searchTweets(query)
            
            response.data.map { tweet ->
                Review(
                    source = "Twitter",
                    content = tweet.text,
                    rating = null,
                    timestamp = tweet.created_at
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isSpamReview(content: String): Boolean {
        return spamPattern.containsMatchIn(content) || 
            content.split(" ").size < 5
    }

    private fun applySentimentAnalysis(review: Review): Review {
        val keywords = TextRank().extractKeywords(review.content)
        val sentiment = keywords.sumOf { word ->
            when (word.lowercase()) {
                "good", "great", "excellent" -> 1.0
                "bad", "terrible", "worst" -> -1.0
                else -> 0.0
            }
        }.coerceIn(-1.0, 1.0)
        
        return review.copy(sentimentScore = sentiment)
    }
}

// TextRank implementation
class TextRank {
    fun extractKeywords(text: String, topN: Int = 5): List<String> {
        val words = text.split(" ").filter { it.length > 3 }
        val graph = mutableMapOf<String, MutableSet<String>>()
        
        // Build co-occurrence graph
        words.windowed(2).forEach { (a, b) ->
            graph.getOrPut(a) { mutableSetOf() }.add(b)
            graph.getOrPut(b) { mutableSetOf() }.add(a)
        }
        
        // Simple ranking (real impl would use PageRank)
        return graph.entries
            .sortedByDescending { it.value.size }
            .take(topN)
            .map { it.key }
    }
}

// Circuit Breaker implementation
class ApiCircuitBreaker(
    private val failureThreshold: Int = 3,
    private val resetTimeout: Long = 60000
) {
    private val failureCounts = mutableMapOf<String, Int>()
    private val resetTimers = mutableMapOf<String, Long>()

    fun recordFailure(apiKey: String) {
        val count = failureCounts.getOrDefault(apiKey, 0) + 1
        failureCounts[apiKey] = count
        
        if (count >= failureThreshold) {
            resetTimers[apiKey] = System.currentTimeMillis() + resetTimeout
        }
    }

    fun shouldBlock(apiKey: String): Boolean {
        val resetTime = resetTimers[apiKey] ?: return false
        return if (System.currentTimeMillis() < resetTime) {
            true
        } else {
            resetTimers.remove(apiKey)
            failureCounts.remove(apiKey)
            false
        }
    }
}

// Rate Limiter implementation
class RateLimiter(private val period: Long, private val unit: TimeUnit) {
    private val delayMillis = unit.toMillis(period)
    private var lastRequest = 0L
    
    suspend fun acquire() {
        val now = System.currentTimeMillis()
        val diff = now - lastRequest
        
        if (diff < delayMillis) {
            delay(delayMillis - diff)
        }
        
        lastRequest = System.currentTimeMillis()
    }
}
