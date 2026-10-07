package com.foodrecommender.app.di

import com.foodrecommender.app.BuildConfig
import com.foodrecommender.app.data.local.FusedCoarseLocationSource
import com.foodrecommender.app.data.local.LocationManager
import com.foodrecommender.app.data.remote.PlacesHttp
import com.foodrecommender.app.data.repository.PlacesRepositoryImpl
import com.foodrecommender.app.presentation.viewmodels.MainViewModel
import com.foodrecommender.core.CoarseLocationSource
import com.foodrecommender.core.MemoryPlaceCache
import com.foodrecommender.core.NearbySearchCoordinator
import com.foodrecommender.core.PlacesRepository
import com.foodrecommender.core.isSafeApiKey
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { LocationManager(androidContext()) }
    single { PlacesHttp(apiKey = BuildConfig.GOOGLE_PLACES_API_KEY) }
    single { MemoryPlaceCache() }
    single<CoarseLocationSource> { FusedCoarseLocationSource(get()) }
    single<PlacesRepository> {
        val http = get<PlacesHttp>()
        val cache = get<MemoryPlaceCache>()
        val coordinator = NearbySearchCoordinator(
            googleConfigured = isSafeApiKey(BuildConfig.GOOGLE_PLACES_API_KEY),
            google = { location, radius -> http.searchGoogle(location, radius) },
            overpass = { location, radius -> http.searchOverpass(location, radius) },
            cacheGet = { id -> cache.get(id) },
            cachePut = { id, places -> cache.put(id, places) },
        )
        PlacesRepositoryImpl(coordinator)
    }
    viewModel {
        MainViewModel(
            locationSource = get(),
            repository = get(),
            clearCacheStore = { get<MemoryPlaceCache>().clear() },
        )
    }
}
