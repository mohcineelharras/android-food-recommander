package com.foodrecommender.app.di

import com.foodrecommender.app.data.local.database.AppDatabase
import com.foodrecommender.app.data.remote.FakePlacesService
import com.foodrecommender.app.data.repository.PlacesRepositoryImpl
import com.foodrecommender.app.domain.repository.PlacesRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().restaurantDao() }
    
    single<PlacesRepository> { 
        PlacesRepositoryImpl(
            placesService = FakePlacesService(),
            restaurantDao = get()
        )
    }
}
