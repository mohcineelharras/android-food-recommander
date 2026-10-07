package com.foodrecommender.core

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class Place(
    val id: String,
    val name: String,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val priceLevel: Int? = null,
) {
    init {
        require(id.isNotBlank() && id.length <= 128) { "invalid place id" }
        require(name.isNotBlank() && name.length <= 120) { "invalid place name" }
        require(rating == null || (rating.isFinite() && rating in 0.0..5.0)) { "invalid rating" }
        require(reviewCount == null || reviewCount in 0..1_000_000) { "invalid review count" }
        require(priceLevel == null || priceLevel in 0..4) { "invalid price level" }
    }
}

data class AnalyzedPlace(
    val place: Place,
    val summary: String,
)

fun analyzePlaces(places: List<Place>): List<AnalyzedPlace> {
    return places
        .sortedByDescending { it.rating ?: -1.0 }
        .map { place -> AnalyzedPlace(place, summaryFor(place)) }
}

fun sanitizeDisplayName(raw: String): String {
    val withoutTags = raw.replace(TAG, "")
    return withoutTags.replace(CONTROLS, "").trim().take(120)
}

fun sanitizeId(raw: String): String = raw.replace(CONTROLS, "").trim().take(128)

fun mapPriceLevel(raw: String?): Int? = when (raw) {
    "PRICE_LEVEL_FREE" -> 0
    "PRICE_LEVEL_INEXPENSIVE" -> 1
    "PRICE_LEVEL_MODERATE" -> 2
    "PRICE_LEVEL_EXPENSIVE" -> 3
    "PRICE_LEVEL_VERY_EXPENSIVE" -> 4
    else -> null
}

fun formatRating(rating: Double): String = String.format(Locale.US, "%.1f", rating)

private fun summaryFor(place: Place): String {
    val rating = place.rating ?: return "No public rating yet"
    val formatted = formatRating(rating)
    val count = place.reviewCount ?: return "Rated $formatted"
    return if (count == 1) {
        "Rated $formatted from 1 review"
    } else {
        "Rated $formatted from $count reviews"
    }
}

private val TAG = Regex("<[^>]*>")
private val CONTROLS = Regex("\\p{Cntrl}")
