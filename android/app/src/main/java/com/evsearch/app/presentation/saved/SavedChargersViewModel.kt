package com.evsearch.app.presentation.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val message: String? = null
)

class SavedChargersViewModel(
    private val repository: ChargerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedChargersUiState())
    val uiState: StateFlow<SavedChargersUiState> = _uiState.asStateFlow()

    init {
        observeSavedChargers()
    }

    private fun observeSavedChargers() {
        viewModelScope.launch {
            repository.getSavedChargersFlow().collect { savedList ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    savedChargers = savedList
                )
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            repository.refreshSavedChargersStatus()
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                message = "충전기 상태가 갱신되었습니다."
            )
        }
    }

    fun updateCustomName(key: String, customName: String?) {
        viewModelScope.launch {
            repository.updateChargerCustomName(key, customName)
            _uiState.value = _uiState.value.copy(
                message = if (customName.isNullOrBlank()) "별칭이 초기화되었습니다." else "별칭이 저장되었습니다."
            )
        }
    }

    fun removeCharger(key: String) {
        viewModelScope.launch {
            repository.removeChargerFromWidget(key)
            _uiState.value = _uiState.value.copy(message = "위젯 등록이 해제되었습니다.")
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    class Factory(private val repository: ChargerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SavedChargersViewModel(repository) as T
        }
    }
}
