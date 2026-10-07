package com.foodrecommender.app.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrecommender.core.AnalyzedPlace
import com.foodrecommender.core.CoarseLocationSource
import com.foodrecommender.core.LocationDeniedException
import com.foodrecommender.core.PlacesRepository
import com.foodrecommender.core.UserMessages
import com.foodrecommender.core.analyzePlaces
import com.foodrecommender.core.messageForFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val locationSource: CoarseLocationSource,
    private val repository: PlacesRepository,
    private val clearCacheStore: suspend () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState

    fun search(radiusMeters: Int) {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try {
                val location = locationSource.current()
                val places = repository.getNearbyPlaces(location, radiusMeters)
                _uiState.value = MainUiState.Ready(analyzePlaces(places))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value = MainUiState.Failed(
                    message = messageForFailure(failure),
                    showSettings = failure is LocationDeniedException,
                )
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.value = MainUiState.Failed(
            message = UserMessages.LOCATION_PERMISSION,
            showSettings = true,
        )
    }

    fun clearSavedRestaurants() {
        viewModelScope.launch {
            try {
                clearCacheStore()
                _uiState.value = MainUiState.Idle
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value = MainUiState.Failed(messageForFailure(failure))
            }
        }
    }
}

sealed class MainUiState {
    data object Idle : MainUiState()
    data object Loading : MainUiState()
    data class Ready(val results: List<AnalyzedPlace>) : MainUiState()
    data class Failed(val message: String, val showSettings: Boolean = false) : MainUiState()
}
