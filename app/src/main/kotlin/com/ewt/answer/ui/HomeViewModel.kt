package com.ewt.answer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ewt.answer.data.AppContainer
import com.ewt.answer.data.EwtRepository
import com.ewt.answer.data.HomeworkGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 主页：试卷列表 + 链接查询 */
class HomeViewModel(private val repo: EwtRepository) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Empty : UiState
        data class Ready(val groups: List<HomeworkGroup>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    fun load(force: Boolean = false) {
        if (!force && _uiState.value is UiState.Ready) return
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _uiState.value = UiState.Loading
            try {
                val groups = repo.scanAllPapers { msg -> _statusText.value = msg }
                _uiState.value = if (groups.isEmpty()) UiState.Empty else UiState.Ready(groups)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "加载失败，请检查网络")
            } finally {
                _refreshing.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(AppContainer.repository) }
        }
    }
}
