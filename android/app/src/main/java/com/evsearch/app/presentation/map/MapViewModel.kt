package com.evsearch.app.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.evsearch.app.data.model.ChargerStation
import com.evsearch.app.data.repository.ChargerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MapUiState(
    val isLoading: Boolean = false,
    val selectedZcode: String = "11", // Default: Seoul
    val selectedZcodeName: String = "서울특별시",
    val stations: List<ChargerStation> = emptyList(),
    val errorMessage: String? = null,
    val selectedStation: ChargerStation? = null
)

class MapViewModel(
    private val repository: ChargerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadStations("11", "서울특별시")
    }

    fun loadStations(zcode: String, zcodeName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedZcode = zcode,
                selectedZcodeName = zcodeName,
                errorMessage = null
            )
            val result = repository.getStations(zcode = zcode)
            result.onSuccess { stations ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    stations = stations
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "충전소 정보를 불러오는데 실패했습니다."
                )
            }
        }
    }

    fun selectStation(station: ChargerStation?) {
        _uiState.value = _uiState.value.copy(selectedStation = station)
    }

    class Factory(private val repository: ChargerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MapViewModel(repository) as T
        }
    }
}
