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
    /** 위젯 목록에 들어 있는 key 집합 */
    val widgetKeys: Set<String> = emptySet(),
    /** 즐겨찾기 목록에 들어 있는 key 집합 */
    val favoriteKeys: Set<String> = emptySet(),
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
            repository.getTrackedChargersFlow().collect { list ->
                _uiState.value = _uiState.value.copy(
                    widgetKeys = list.filter { it.isWidget }.map { it.key }.toSet(),
                    favoriteKeys = list.filter { it.isFavorite }.map { it.key }.toSet()
                )
            }
        }
    }

    /** 앞의 6대를 위젯 목록에 한 번에 넣는다(홈 위젯 최대 표시 수). */
    fun registerFirst6Chargers() {
        val station = _uiState.value.station ?: return
        val chargersToSave = station.chargers.sortedBy { it.chgerId }.take(6)
        viewModelScope.launch {
            repository.addChargersToWidget(station, chargersToSave)
            _uiState.value = _uiState.value.copy(
                widgetSavedSuccessMessage = "${chargersToSave.size}대를 위젯에 추가했습니다."
            )
        }
    }

    fun toggleWidgetRegistration(charger: Charger) {
        val station = _uiState.value.station ?: return
        val key = "${station.statId}:${charger.chgerId}"
        viewModelScope.launch {
            if (_uiState.value.widgetKeys.contains(key)) {
                repository.removeChargerFromWidget(key)
                _uiState.value = _uiState.value.copy(widgetSavedSuccessMessage = "위젯에서 제거했습니다.")
            } else {
                repository.addChargerToWidget(station, charger)
                _uiState.value = _uiState.value.copy(widgetSavedSuccessMessage = "위젯에 추가했습니다.")
            }
        }
    }

    /** 즐겨찾기(빈자리 알림 대상) 토글. 위젯 목록과는 독립적이다. */
    fun toggleFavorite(charger: Charger) {
        val station = _uiState.value.station ?: return
        val key = "${station.statId}:${charger.chgerId}"
        viewModelScope.launch {
            if (_uiState.value.favoriteKeys.contains(key)) {
                repository.removeChargerFromFavorites(key)
                _uiState.value = _uiState.value.copy(widgetSavedSuccessMessage = "즐겨찾기에서 제거했습니다.")
            } else {
                repository.addChargerToFavorites(station, charger)
                _uiState.value = _uiState.value.copy(
                    widgetSavedSuccessMessage = "즐겨찾기에 추가했습니다. 알림 설정은 즐겨찾기 탭에서 조정할 수 있습니다."
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
