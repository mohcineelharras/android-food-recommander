package com.foodrecommender.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.foodrecommender.app.data.local.dao.RestaurantDao
import com.foodrecommender.app.domain.models.RestaurantEntity

@Database(entities = [RestaurantEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun restaurantDao(): RestaurantDao
}
