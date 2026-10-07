package com.foodrecommender.app.data.repository

import com.foodrecommender.core.LatLng
import com.foodrecommender.core.NearbySearchCoordinator
import com.foodrecommender.core.Place
import com.foodrecommender.core.PlacesRepository

class PlacesRepositoryImpl(
    private val coordinator: NearbySearchCoordinator,
) : PlacesRepository {
    override suspend fun getNearbyPlaces(location: LatLng, radiusMeters: Int): List<Place> {
        return coordinator.search(location, radiusMeters)
    }
}
