package com.foodrecommender.app.data.local.dao

import androidx.room.*
import com.foodrecommender.app.data.local.CachedPlaces

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedPlaces(cached: CachedPlaces)

    @Query("DELETE FROM cached_places WHERE timestamp < :expiry")
    suspend fun deleteExpired(expiry: Long)

    @Query("SELECT * FROM cached_places WHERE latitude = :location.lat AND longitude = :location.lng AND radius = :radius")
    suspend fun getCachedPlaces(location: LatLng, radius: Int): CachedPlaces?
}

@Entity(tableName = "cached_places")
data class CachedPlaces(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    val radius: Int,
    val places: List<Place>,
    val timestamp: Long
)
