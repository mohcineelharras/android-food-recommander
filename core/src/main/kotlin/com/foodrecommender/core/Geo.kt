package com.foodrecommender.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class LatLng(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && longitude.isFinite()) { "invalid coordinates" }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "invalid coordinates" }
    }

    fun coarsen(decimals: Int = COARSE_DECIMALS): LatLng {
        return LatLng(roundDecimal(latitude, decimals), roundDecimal(longitude, decimals))
    }
}

private fun roundDecimal(value: Double, decimals: Int): Double {
    return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toDouble()
}

const val COARSE_DECIMALS = 2
const val MIN_RADIUS_METERS = 100
const val MAX_RADIUS_METERS = 50_000

fun sanitizeRadiusMeters(radiusMeters: Int): Int {
    require(radiusMeters in MIN_RADIUS_METERS..MAX_RADIUS_METERS) { "invalid radius" }
    return radiusMeters
}

fun cacheKey(location: LatLng, radiusMeters: Int): String {
    val coarse = location.coarsen()
    val radius = sanitizeRadiusMeters(radiusMeters)
    val lat = formatCoordinate(coarse.latitude)
    val lng = formatCoordinate(coarse.longitude)
    return "$lat,$lng,$radius"
}

fun buildRestaurantOverpassQuery(location: LatLng, radiusMeters: Int): String {
    val coarse = location.coarsen()
    val radius = sanitizeRadiusMeters(radiusMeters)
    val lat = formatCoordinate(coarse.latitude)
    val lng = formatCoordinate(coarse.longitude)
    require(DECIMAL.matches(lat) && DECIMAL.matches(lng)) { "invalid coordinate format" }
    return "[out:json][timeout:25];\n" +
        "node[\"amenity\"~\"^(restaurant|cafe|fast_food|bakery)$\"](around:$radius,$lat,$lng);\n" +
        "out body 40;"
}

private val DECIMAL = Regex("-?\\d+\\.\\d{2}")

internal fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.2f", value)
