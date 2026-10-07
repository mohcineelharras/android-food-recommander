package com.foodrecommender.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val placesJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private const val MAX_PLACES = 40

@Serializable
private data class OverpassResponse(val elements: List<OverpassElement> = emptyList())

@Serializable
private data class OverpassElement(
    val id: Long = 0,
    val tags: Map<String, String> = emptyMap(),
)

@Serializable
private data class GoogleNearbyResponse(val places: List<GooglePlace> = emptyList())

@Serializable
private data class GooglePlace(
    val id: String = "",
    val displayName: GoogleName? = null,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val priceLevel: String? = null,
)

@Serializable
private data class GoogleName(val text: String = "")

fun parseOverpassPlaces(body: String): List<Place> {
    ensureBodyLimit(body)
    val parsed = placesJson.decodeFromString<OverpassResponse>(body)
    return parsed.elements.mapNotNull { element ->
        val name = sanitizeDisplayName(element.tags["name"].orEmpty())
        val id = sanitizeId(element.id.toString())
        toPlace(id, name, rating = null, reviewCount = null, priceLevel = null)
    }.take(MAX_PLACES)
}

fun parseGooglePlaces(body: String): List<Place> {
    ensureBodyLimit(body)
    val parsed = placesJson.decodeFromString<GoogleNearbyResponse>(body)
    return parsed.places.mapNotNull { place ->
        toPlace(
            id = sanitizeId(place.id),
            name = sanitizeDisplayName(place.displayName?.text.orEmpty()),
            rating = place.rating?.takeIf { it.isFinite() && it in 0.0..5.0 },
            reviewCount = place.userRatingCount?.takeIf { it in 0..1_000_000 },
            priceLevel = mapPriceLevel(place.priceLevel),
        )
    }.take(MAX_PLACES)
}

fun placesToJson(places: List<Place>): String {
    return placesJson.encodeToString(ListSerializer(Place.serializer()), places)
}

fun placesFromJson(payload: String): List<Place> {
    ensureBodyLimit(payload)
    return placesJson.decodeFromString(ListSerializer(Place.serializer()), payload)
}

private fun toPlace(
    id: String,
    name: String,
    rating: Double?,
    reviewCount: Int?,
    priceLevel: Int?,
): Place? {
    if (id.isBlank() || name.isBlank()) return null
    return Place(
        id = id,
        name = name,
        rating = rating,
        reviewCount = reviewCount,
        priceLevel = priceLevel,
    )
}

private fun ensureBodyLimit(body: String) {
    if (body.length > MAX_PLACES_BODY_CHARS) {
        throw IllegalArgumentException(UserMessages.RESPONSE_TOO_LARGE)
    }
}
