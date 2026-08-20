package com.ewt.answer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ewt.answer.data.CourseRepository
import com.ewt.answer.data.VideoLesson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 课程页 ViewModel：原生视频课扫描 + 刷课（竞态爆发上报） */
class CourseViewModel(private val repo: CourseRepository) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val groups: List<CourseGroup>) : UiState
        data class Error(val message: String) : UiState
    }

    data class CourseGroup(val homeworkTitle: String, val lessons: List<VideoLesson>)

    data class LessonUi(
        val lesson: VideoLesson,
        val percent: Int = 0,
        val running: Boolean = false,
        val done: Boolean = false,
        val failed: Boolean = false,
        val message: String = "",
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val _lessons = MutableStateFlow<Map<String, LessonUi>>(emptyMap())
    val lessons: StateFlow<Map<String, LessonUi>> = _lessons

    private val _brushingAll = MutableStateFlow(false)
    val brushingAll: StateFlow<Boolean> = _brushingAll

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    private var brushJob: Job? = null

    fun load() {
        if (_uiState.value is UiState.Ready || _uiState.value is UiState.Loading) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val list = repo.scanVideoLessons { _statusText.value = it }
                val groups = list.groupBy { it.homeworkTitle }
                    .map { (t, ls) -> CourseGroup(t, ls) }
                _lessons.value = list.associate { it.lessonId to LessonUi(lesson = it) }
                _uiState.value = if (groups.isEmpty()) {
                    UiState.Error("未找到视频课时")
                } else {
                    UiState.Ready(groups)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "加载失败，请检查网络")
            }
        }
    }

    private fun update(id: String, transform: (LessonUi) -> LessonUi) {
        val cur = _lessons.value[id] ?: return
        _lessons.value = _lessons.value + (id to transform(cur))
    }

    private fun applyResult(lessonId: String, r: com.ewt.answer.data.BrushResult) {
        update(lessonId) {
            it.copy(running = false, done = r.success, failed = !r.success, message = r.message)
        }
    }

    /** 单课时刷课 */
    fun brushLesson(lesson: VideoLesson) {
        if (brushJob?.isActive == true) return
        update(lesson.lessonId) {
            it.copy(running = true, done = false, failed = false, percent = 0, message = "初始化…")
        }
        brushJob = viewModelScope.launch {
            val r = repo.brushLesson(lesson) { msg ->
                update(lesson.lessonId) { cur ->
                    val pct = msg.substringBefore("%").toIntOrNull()
                    cur.copy(running = true, message = msg, percent = pct ?: cur.percent)
                }
            }
            applyResult(lesson.lessonId, r)
        }
    }

    /** 一键刷全部未完成课时（串行，可停止） */
    fun brushAll() {
        if (brushJob?.isActive == true) return
        viewModelScope.launch {
            _brushingAll.value = true
            _summary.value = ""
            val pending = _lessons.value.values
                .filter { !it.done && !it.running }
                .map { it.lesson }
            if (pending.isEmpty()) {
                _summary.value = "没有未完成的课时"
                _brushingAll.value = false
                return@launch
            }
            var ok = 0
            pending.forEachIndexed { i, lesson ->
                if (!isActive) return@launch
                _summary.value = "刷课 ${i + 1}/${pending.size}：${lesson.title}"
                update(lesson.lessonId) {
                    it.copy(running = true, done = false, failed = false, percent = 0, message = "初始化…")
                }
                val r = repo.brushLesson(lesson) { msg ->
                    _summary.value = "刷课 ${i + 1}/${pending.size}：${lesson.title} $msg"
                    update(lesson.lessonId) { cur ->
                        val pct = msg.substringBefore("%").toIntOrNull()
                        cur.copy(running = true, message = msg, percent = pct ?: cur.percent)
                    }
                }
                applyResult(lesson.lessonId, r)
                if (r.success) ok++
            }
            if (isActive) {
                _summary.value = "批量完成：$ok/${pending.size}"
            }
            _brushingAll.value = false
        }.also { brushJob = it }
    }

    /** 停止当前刷课任务 */
    fun stop() {
        brushJob?.cancel()
        brushJob = null
        _brushingAll.value = false
        _summary.value = "已停止"
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { CourseViewModel(CourseRepository()) }
        }
    }
}
