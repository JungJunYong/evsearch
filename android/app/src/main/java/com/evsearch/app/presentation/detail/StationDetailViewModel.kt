package com.evsearch.app.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.evsearch.app.data.model.Charger
import com.evsearch.app.data.model.ChargerStation
import com.evsearch.app.data.repository.ChargerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StationDetailUiState(
    val isLoading: Boolean = false,
    val station: ChargerStation? = null,
    val errorMessage: String? = null,
    val savedChargerKeys: Set<String> = emptySet(),
    val widgetSavedSuccessMessage: String? = null
)

class StationDetailViewModel(
    private val repository: ChargerRepository,
    private val statId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(StationDetailUiState())
    val uiState: StateFlow<StationDetailUiState> = _uiState.asStateFlow()

    init {
        loadStationDetail()
        observeSavedChargers()
    }

    fun loadStationDetail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.getStationDetail(statId)
            result.onSuccess { station ->
                _uiState.value = _uiState.value.copy(isLoading = false, station = station)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "충전소 상세 정보를 불러오는데 실패했습니다."
                )
            }
        }
    }

    private fun observeSavedChargers() {
        viewModelScope.launch {
            repository.getSavedChargersFlow().collect { savedList ->
                val keySet = savedList.map { it.key }.toSet()
                _uiState.value = _uiState.value.copy(savedChargerKeys = keySet)
            }
        }
    }

    fun toggleWidgetRegistration(charger: Charger) {
        val station = _uiState.value.station ?: return
        val key = "${station.statId}:${charger.chgerId}"
        val displayName = charger.chargerCode ?: charger.chgerId
        viewModelScope.launch {
            if (_uiState.value.savedChargerKeys.contains(key)) {
                repository.removeChargerFromWidget(key)
                _uiState.value = _uiState.value.copy(
                    widgetSavedSuccessMessage = "충전기 [${displayName}] 위젯 등록이 해제되었습니다."
                )
            } else {
                repository.saveChargerToWidget(station, charger)
                _uiState.value = _uiState.value.copy(
                    widgetSavedSuccessMessage = "충전기 [${displayName}] 번이 홈 화면 위젯에 등록되었습니다!"
                )
            }
        }
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(widgetSavedSuccessMessage = null)
    }

    class Factory(
        private val repository: ChargerRepository,
        private val statId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StationDetailViewModel(repository, statId) as T
        }
    }
}
