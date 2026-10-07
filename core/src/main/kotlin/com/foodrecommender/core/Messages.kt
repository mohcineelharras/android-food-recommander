package com.foodrecommender.core

object UserMessages {
    const val PLACES = "Could not load nearby restaurants. Check your connection and try again."
    const val LOCATION_PERMISSION =
        "Location permission is needed to find nearby restaurants. You can allow approximate location, or enable it later in system settings."
    const val LOCATION_UNAVAILABLE = "Could not determine an approximate area. Try again in a moment."
    const val RESPONSE_TOO_LARGE = "response too large"
}

class LocationDeniedException : Exception()

class LocationUnavailableException : Exception()

class SearchException : Exception(UserMessages.PLACES)

class PlacesNotConfiguredException : Exception()

fun messageForFailure(error: Throwable): String = when (error) {
    is LocationDeniedException -> UserMessages.LOCATION_PERMISSION
    is LocationUnavailableException -> UserMessages.LOCATION_UNAVAILABLE
    else -> UserMessages.PLACES
}

fun isSafeApiKey(key: String): Boolean {
    if (key.length !in 1..256) return false
    return key.all { character ->
        character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' || character == '-' || character == '_'
    }
}

object PlacesEndpoints {
    const val GOOGLE_HOST = "places.googleapis.com"
    const val OVERPASS_HOST = "overpass-api.de"
    const val GOOGLE_URL = "https://places.googleapis.com/v1/places:searchNearby"
    const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"

    fun isAllowedHost(host: String): Boolean = host == GOOGLE_HOST || host == OVERPASS_HOST
}
