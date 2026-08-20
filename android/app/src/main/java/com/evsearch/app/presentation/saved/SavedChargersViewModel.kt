package com.evsearch.app.presentation.saved

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.evsearch.app.alert.AlertPrefs
import com.evsearch.app.data.local.SavedChargerEntity
import com.evsearch.app.data.repository.ChargerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SavedChargersUiState(
    val isLoading: Boolean = true,
    val savedChargers: List<SavedChargerEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastPushAt: Long = 0L,
    val message: String? = null
)

/**
 * 위젯 목록 화면. 홈 화면 위젯에 표시할 단말기만 다룬다(즐겨찾기와 별도 목록).
 * 서버가 상태 변화를 감지하면 푸시로 즉시 갱신되고, 그 밖에는 15분 고정 주기로 돈다.
 */
class SavedChargersViewModel(
    private val repository: ChargerRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedChargersUiState())
    val uiState: StateFlow<SavedChargersUiState> = _uiState.asStateFlow()

    init {
        observeWidgetChargers()
        reloadSettings()
    }

    private fun observeWidgetChargers() {
        viewModelScope.launch {
            repository.getWidgetChargersFlow().collect { list ->
                _uiState.value = _uiState.value.copy(isLoading = false, savedChargers = list)
            }
        }
    }

    fun reloadSettings() {
        _uiState.value = _uiState.value.copy(
            lastSyncAt = AlertPrefs.getLastSyncAt(appContext),
            lastPushAt = AlertPrefs.getLastPushAt(appContext)
        )
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            repository.refreshTrackedChargersStatus(maxAgeMs = 0L)
            AlertPrefs.setLastSyncAt(appContext, System.currentTimeMillis())
            _uiState.value = _uiState.value.copy(isRefreshing = false, message = "위젯을 최신 상태로 갱신했습니다.")
            reloadSettings()
        }
    }

    fun updateCustomName(key: String, customName: String?) {
        viewModelScope.launch {
            repository.updateChargerCustomName(key, customName)
            _uiState.value = _uiState.value.copy(
                message = if (customName.isNullOrBlank()) "별칭을 초기화했습니다." else "별칭을 저장했습니다."
            )
        }
    }

    fun removeCharger(key: String) {
        viewModelScope.launch {
            repository.removeChargerFromWidget(key)
            _uiState.value = _uiState.value.copy(message = "위젯에서 제거했습니다.")
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    class Factory(
        private val repository: ChargerRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SavedChargersViewModel(repository, appContext) as T
        }
    }
}
