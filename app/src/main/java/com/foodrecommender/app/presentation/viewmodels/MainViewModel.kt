package com.foodrecommender.app.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrecommender.app.domain.usecases.GetNearbyPlaces
import com.foodrecommender.app.domain.usecases.AnalyzeReviews
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val getNearbyPlaces: GetNearbyPlaces,
    private val analyzeReviews: AnalyzeReviews
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState get() = _uiState

    fun searchPlaces(radius: Int) {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try {
                val places = getNearbyPlaces(radius)
                val analyzed = analyzeReviews(places)
                _uiState.value = MainUiState.Success(analyzed)
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class MainUiState {
    object Idle : MainUiState()
    object Loading : MainUiState()
    data class Success(val results: List<AnalyzedPlace>) : MainUiState()
    data class Error(val message: String) : MainUiState()
}
