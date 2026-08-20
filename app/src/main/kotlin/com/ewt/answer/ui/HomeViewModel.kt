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

/** 主页：试卷列表 + 链接查询 + 日期/学科筛选（顶栏三杠弹窗）+ 搜索 + 一键刷今日（未完工） */
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

    private val _brushing = MutableStateFlow(false)
    val brushing: StateFlow<Boolean> = _brushing

    /** 日期筛选（"MM-dd" 或 null=全部） */
    private val _dateFilter = MutableStateFlow<String?>(null)
    val dateFilter: StateFlow<String?> = _dateFilter

    /** 学科筛选（学科名或 null=全部） */
    private val _subjectFilter = MutableStateFlow<String?>(null)
    val subjectFilter: StateFlow<String?> = _subjectFilter

    /** 搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

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

    /** 一键刷今天所有试卷（预留给正式版；当前入口未开放仅提示） */
    fun brushToday(onProgress: (String) -> Unit, onDone: (String) -> Unit) {
        if (_brushing.value) return
        viewModelScope.launch {
            _brushing.value = true
            try {
                var groups = (uiState.value as? UiState.Ready)?.groups
                if (groups == null) {
                    groups = repo.scanAllPapers {}
                    _uiState.value = if (groups.isEmpty()) UiState.Empty else UiState.Ready(groups)
                }
                val today = formatToday()
                val papers = groups.flatMap { it.papers }.filter { it.date == today }
                if (papers.isEmpty()) {
                    onDone("今天（$today）没有可刷的试卷")
                    return@launch
                }
                val sb = StringBuilder("今日刷卷（$today）共 ${papers.size} 张：")
                papers.forEachIndexed { i, p ->
                    onProgress("刷卷 ${i + 1}/${papers.size}：${p.title}")
                    try {
                        repo.brushPaper(p) { onProgress("刷卷 ${i + 1}/${papers.size}：$it") }
                        sb.append("\n✓ ").append(p.title)
                    } catch (e: Exception) {
                        sb.append("\n✗ ").append(p.title).append("：").append(e.message)
                    }
                }
                onDone(sb.toString())
            } catch (e: Exception) {
                onDone("刷卷失败：${e.message}")
            } finally {
                _brushing.value = false
            }
        }
    }

    fun setDateFilter(value: String?) {
        _dateFilter.value = value
    }

    fun setSubjectFilter(value: String?) {
        _subjectFilter.value = value
    }

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    private fun formatToday(): String {
        val d = java.util.Date()
        return String.format("%02d-%02d", d.month + 1, d.date)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(AppContainer.repository) }
        }
    }
}
