package com.foodrecommender.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface GooglePlacesService {
    @GET("maps/api/place/nearbysearch/json")
    suspend fun nearbySearch(
        @Query("location") location: String,
        @Query("radius") radius: Int,
        @Query("type") types: String,
        @Query("fields") fields: String
    ): PlacesResponse
}

data class PlacesResponse(val results: List<ApiPlace>)
data class ApiPlace(
    val place_id: String,
    val name: String,
    val rating: Double?,
    val user_ratings_total: Int?,
    val price_level: Int?
)
