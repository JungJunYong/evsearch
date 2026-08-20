package com.evsearch.app.presentation.saved

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.evsearch.app.alert.AlertPrefs
import com.evsearch.app.data.local.SavedChargerEntity
import com.evsearch.app.data.repository.ChargerRepository
import com.evsearch.app.widget.WidgetSyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SavedChargersUiState(
    val isLoading: Boolean = true,
    val savedChargers: List<SavedChargerEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    /** 위젯 자동 갱신 주기(초). */
    val widgetIntervalSec: Int = 300,
    val lastSyncAt: Long = 0L,
    val lastPushAt: Long = 0L,
    val message: String? = null
)

/**
 * 위젯 목록 화면. 홈 화면 위젯에 표시할 단말기만 다룬다(즐겨찾기와 별도 목록).
 * 갱신 주기를 사용자가 정하고, 서버 푸시가 오면 그 즉시 위젯이 다시 그려진다.
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
            widgetIntervalSec = AlertPrefs.getWidgetIntervalSec(appContext),
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

    /** 위젯 자동 갱신 주기 변경. 체인 작업을 새 주기로 다시 예약한다. */
    fun setWidgetIntervalSec(sec: Int) {
        AlertPrefs.setWidgetIntervalSec(appContext, sec)
        WidgetSyncScheduler.onIntervalChanged(appContext)
        reloadSettings()
        _uiState.value = _uiState.value.copy(message = "자동 갱신 주기를 변경했습니다.")
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
