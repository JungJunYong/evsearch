package com.evsearch.app.presentation.favorites

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

data class AlertSettings(
    val enabled: Boolean = false,
    val startMin: Int = 18 * 60,
    val endMin: Int = 23 * 60,
    val intervalSec: Int = 60
) {
    val isAllDay: Boolean get() = startMin == endMin
}

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val favorites: List<SavedChargerEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val settings: AlertSettings = AlertSettings(),
    val lastPushAt: Long = 0L,
    val message: String? = null
)

/**
 * 즐겨찾기 목록 + 빈자리 알림 설정.
 *
 * 즐겨찾기는 위젯 목록과 완전히 별개의 목록이며, 서버 감시(알림) 대상은 즐겨찾기 쪽이다.
 * 시간 범위와 확인 주기를 사용자가 정하고, 서버가 그 주기로 상태를 보며 빈자리 전환
 * 순간에 FCM 푸시를 보낸다.
 */
class FavoritesViewModel(
    private val repository: ChargerRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        observeFavorites()
        reloadSettings()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.getFavoriteChargersFlow().collect { list ->
                _uiState.value = _uiState.value.copy(isLoading = false, favorites = list)
            }
        }
    }

    fun reloadSettings() {
        _uiState.value = _uiState.value.copy(
            settings = AlertSettings(
                enabled = AlertPrefs.getEnabled(appContext),
                startMin = AlertPrefs.getStartMin(appContext),
                endMin = AlertPrefs.getEndMin(appContext),
                intervalSec = AlertPrefs.getIntervalSec(appContext)
            ),
            lastPushAt = AlertPrefs.getLastPushAt(appContext)
        )
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            repository.refreshTrackedChargersStatus(maxAgeMs = 0L)
            _uiState.value = _uiState.value.copy(isRefreshing = false, message = "최신 상태로 갱신했습니다.")
        }
    }

    /** 알림 마스터 스위치. 켤 때는 현재 설정으로 서버 구독을 등록한다. */
    fun setAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val s = _uiState.value.settings
            if (enabled) {
                if (_uiState.value.favorites.isEmpty()) {
                    _uiState.value = _uiState.value.copy(message = "먼저 즐겨찾기에 충전기를 추가해 주세요.")
                    return@launch
                }
                val r = repository.subscribeVacancyAlert(s.startMin, s.endMin)
                reloadSettings()
                _uiState.value = _uiState.value.copy(
                    message = if (r.isSuccess) "빈자리 알림을 켰습니다." else "알림 설정 실패: ${r.exceptionOrNull()?.message}"
                )
            } else {
                repository.disableVacancyAlert()
                reloadSettings()
                _uiState.value = _uiState.value.copy(message = "빈자리 알림을 껐습니다.")
            }
        }
    }

    /** 감시 시간 범위 변경 (start == end 이면 종일). */
    fun setWindow(startMin: Int, endMin: Int) {
        viewModelScope.launch {
            AlertPrefs.setStartMin(appContext, startMin)
            AlertPrefs.setEndMin(appContext, endMin)
            reloadSettings()
            if (_uiState.value.settings.enabled) {
                repository.syncAlertSubscription()
            }
        }
    }

    fun setAllDay(allDay: Boolean) {
        if (allDay) setWindow(0, 0) else setWindow(18 * 60, 23 * 60)
    }

    /** 항목별 알림 수신 여부. */
    fun setItemAlertEnabled(key: String, enabled: Boolean) {
        viewModelScope.launch { repository.setChargerAlertEnabled(key, enabled) }
    }

    fun removeFavorite(key: String) {
        viewModelScope.launch {
            repository.removeChargerFromFavorites(key)
            _uiState.value = _uiState.value.copy(message = "즐겨찾기에서 제거했습니다.")
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

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    class Factory(
        private val repository: ChargerRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FavoritesViewModel(repository, appContext) as T
        }
    }
}
