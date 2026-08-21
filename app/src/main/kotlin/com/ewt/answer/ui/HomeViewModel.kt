package com.ewt.answer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ewt.answer.data.AppContainer
import com.ewt.answer.data.EwtRepository
import com.ewt.answer.data.HomeworkGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 主页：试卷列表 + 链接查询 + 日期/学科筛选（三条杠锚点弹窗 + 滚轮）+ 搜索 + 一键刷今日（选日期刷卷，可暂停） */
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

    /** 一键刷今日：当前进度文案 */
    private val _brushProgress = MutableStateFlow("")
    val brushProgress: StateFlow<String> = _brushProgress

    /** 一键刷今日：结果文案（null = 无结果） */
    private val _brushResult = MutableStateFlow<String?>(null)
    val brushResult: StateFlow<String?> = _brushResult

    /** 一键刷今日：是否暂停中 */
    private val _brushPaused = MutableStateFlow(false)
    val brushPaused: StateFlow<Boolean> = _brushPaused

    /** 暂停标志：true=暂停，false=继续（跨协程检查用） */
    private val pauseRequested = kotlinx.coroutines.flow.MutableStateFlow(false)

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

    /** 暂停 / 继续 一键刷今日 */
    fun toggleBrushPause() {
        if (!_brushing.value) return
        val nowPaused = !pauseRequested.value
        pauseRequested.value = nowPaused
        _brushPaused.value = nowPaused
    }

    /**
     * 一键刷指定日期所有试卷：打开 → 题目 → 解锁 → 逐题答案 → 提交交卷自批。
     * 进度写入 [brushProgress]，最终结果写入 [brushResult]；可暂停/继续。
     */
    fun brushToday(date: String) {
        if (_brushing.value) return
        viewModelScope.launch {
            _brushing.value = true
            _brushProgress.value = ""
            _brushResult.value = null
            pauseRequested.value = false
            _brushPaused.value = false
            try {
                var groups = (uiState.value as? UiState.Ready)?.groups
                if (groups == null) {
                    groups = repo.scanAllPapers { _brushProgress.value = it }
                    _uiState.value = if (groups.isEmpty()) UiState.Empty else UiState.Ready(groups)
                }
                val papers = groups.flatMap { it.papers }.filter { it.date == date }
                if (papers.isEmpty()) {
                    _brushResult.value = "所选日期（$date）没有可刷的试卷"
                    return@launch
                }
                val sb = StringBuilder("刷卷（$date）共 ${papers.size} 张：")
                papers.forEachIndexed { i, p ->
                    // 暂停检查：暂停时等待继续
                    while (pauseRequested.value) {
                        _brushProgress.value = "⏸ 已暂停（点击继续恢复）"
                        delay(500)
                    }
                    _brushProgress.value = "刷卷 ${i + 1}/${papers.size}：${p.title}"
                    try {
                        repo.brushPaper(p) { msg ->
                            _brushProgress.value = "刷卷 ${i + 1}/${papers.size}：$msg"
                        }
                        sb.append("\n✓ ").append(p.title)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        sb.append("\n✗ ").append(p.title).append("：").append(e.message)
                    }
                }
                _brushResult.value = sb.toString()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _brushResult.value = "刷卷失败：${e.message}"
            } finally {
                _brushing.value = false
                _brushPaused.value = false
                pauseRequested.value = false
            }
        }
    }

    fun clearBrushResult() {
        _brushResult.value = null
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

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(AppContainer.repository) }
        }
    }
}
