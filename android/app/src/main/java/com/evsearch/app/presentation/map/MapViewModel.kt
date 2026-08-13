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
    val selectedZcode: String = "all", // Default: Nationwide (전국)
    val selectedZcodeName: String = "전국",
    val stations: List<ChargerStation> = emptyList(),
    val errorMessage: String? = null,
    val selectedStation: ChargerStation? = null
)

class MapViewModel(
    private val repository: ChargerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Client-side Memory Cache for Instant Region Switching & Map Browsing (0ms delay)
    private var nationwideMasterCache: List<ChargerStation>? = null
    private val regionCacheMap = mutableMapOf<String, List<ChargerStation>>()

    init {
        loadStations("all", "전국")
    }

    fun loadStations(zcode: String = "all", zcodeName: String = "전국") {
        viewModelScope.launch {
            // 1. Instant Cache Hit Check (0ms UI latency)
            val cachedStations = getCachedStations(zcode)
            if (cachedStations != null && cachedStations.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    selectedZcode = zcode,
                    selectedZcodeName = zcodeName,
                    stations = cachedStations,
                    errorMessage = null
                )
                return@launch
            }

            // 2. Fetch from API only if not cached in memory
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.stations.isEmpty(), // Only set loading if no stations displayed
                selectedZcode = zcode,
                selectedZcodeName = zcodeName,
                errorMessage = null
            )

            val targetZcode = if (zcode == "all") null else zcode
            val result = repository.getStations(zcode = targetZcode)
            result.onSuccess { stations ->
                if (zcode == "all" || targetZcode == null) {
                    nationwideMasterCache = stations
                } else {
                    regionCacheMap[zcode] = stations
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    stations = stations
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (_uiState.value.stations.isEmpty()) error.message else null
                )
            }
        }
    }

    private fun getCachedStations(zcode: String): List<ChargerStation>? {
        if (zcode == "all" || zcode.isBlank()) {
            return nationwideMasterCache
        }
        val master = nationwideMasterCache
        if (master != null && master.isNotEmpty()) {
            return master.filter { it.zcode == zcode }
        }
        return regionCacheMap[zcode]
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
