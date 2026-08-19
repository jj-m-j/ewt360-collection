package com.ewt.answer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ewt.answer.data.AppContainer
import com.ewt.answer.data.EwtRepository
import com.ewt.answer.data.Paper
import com.ewt.answer.data.PaperSession
import com.ewt.answer.data.QuestionAnswer
import com.ewt.answer.data.QuestionItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 题目页 ViewModel
 *
 * 流程：打开试卷 → reportId → 题目列表（题组/非题组）
 *   → 空交卷解锁 → 并发拉取答案（信号量限流 4）→ 进度 / 失败重试
 *   → 用户确认后提交答案（选择题标准答案 + 非选择题自批）并交卷自批
 */
class QuestionsViewModel(
    private val repo: EwtRepository,
    private val paper: Paper,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(
            val session: PaperSession,
            val questions: List<QuestionItem>,
        ) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    /** questionId → 答案 */
    private val _answers = MutableStateFlow<Map<String, QuestionAnswer>>(emptyMap())
    val answers: StateFlow<Map<String, QuestionAnswer>> = _answers

    /** 获取失败的 questionId 集合 */
    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed

    private val _fetching = MutableStateFlow(false)
    val fetching: StateFlow<Boolean> = _fetching

    /** 已完成数（用于进度显示 N / total） */
    private val _done = MutableStateFlow(0)
    val done: StateFlow<Int> = _done

    /** 提交中 */
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting

    /** 提交结果描述 */
    private val _submitResult = MutableStateFlow<String?>(null)
    val submitResult: StateFlow<String?> = _submitResult

    private var session: PaperSession? = null

    fun load() {
        if (_fetching.value) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val s = repo.openPaper(paper)
                session = s
                val qs = repo.fetchQuestions(s)
                _uiState.value = UiState.Ready(s, qs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "获取题目失败")
            }
        }
    }

    /** 获取全部题目答案（先空交卷解锁，并发上限 4，防限流 / ANR） */
    fun fetchAllAnswers() {
        val s = session ?: return
        val state = _uiState.value as? UiState.Ready ?: return
        if (_fetching.value) return
        viewModelScope.launch {
            // 空交卷解锁：答案/解析接口在交卷后才返回内容
            runCatching { repo.unlockPaper(s) }
            fetchAnswersInternal(s, state.questions)
        }
    }

    /** 仅重试失败的题目 */
    fun retryFailed() {
        val s = session ?: return
        val state = _uiState.value as? UiState.Ready ?: return
        if (_fetching.value) return
        val failedIds = _failed.value
        if (failedIds.isEmpty()) return
        val target = state.questions.filter { it.questionId in failedIds }
        viewModelScope.launch {
            fetchAnswersInternal(s, target)
        }
    }

    /** 重试单题 */
    fun retryOne(question: QuestionItem) {
        val s = session ?: return
        if (_fetching.value) return
        viewModelScope.launch {
            val counter = AtomicInteger(0)
            val results = _answers.value.toMutableMap()
            val failedIds = _failed.value.toMutableSet()
            launch(Dispatchers.IO) {
                try {
                    val a = repo.fetchAnswer(s, question)
                    if (a != null) {
                        results[question.questionId] = a
                        failedIds.remove(question.questionId)
                    } else {
                        failedIds.add(question.questionId)
                    }
                } catch (e: Exception) {
                    failedIds.add(question.questionId)
                }
                counter.incrementAndGet()
                _done.value = counter.get()
                _answers.value = results.toMap()
                _failed.value = failedIds.toSet()
            }
        }
    }

    /** 提交整卷答案（用户已确认）：选择题标准答案 + 非选择题自批 + 交卷自批 */
    fun submitAnswers() {
        val state = _uiState.value as? UiState.Ready ?: return
        if (_submitting.value) return
        if (_answers.value.isEmpty()) return
        viewModelScope.launch {
            _submitting.value = true
            _submitResult.value = null
            try {
                val msg = repo.submitPaperAnswers(paper, state.questions, _answers.value)
                _submitResult.value = msg
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _submitResult.value = "提交失败：${e.message}"
            } finally {
                _submitting.value = false
            }
        }
    }

    /** 清除提交结果提示 */
    fun clearSubmitResult() {
        _submitResult.value = null
    }

    private suspend fun fetchAnswersInternal(s: PaperSession, questions: List<QuestionItem>) {
        _fetching.value = true
        _done.value = 0
        if (questions.isEmpty()) {
            _fetching.value = false
            return
        }
        val semaphore = Semaphore(4)
        val counter = AtomicInteger(0)
        val results = ConcurrentHashMap<String, QuestionAnswer>()
        val failedIds = ConcurrentHashMap.newKeySet<String>()
        try {
            coroutineScope {
                questions.map { q ->
                    launch(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                val a = repo.fetchAnswer(s, q)
                                if (a != null) {
                                    results[q.questionId] = a
                                } else {
                                    failedIds.add(q.questionId)
                                }
                            } catch (e: Exception) {
                                failedIds.add(q.questionId)
                            }
                        }
                        val doneCount = counter.incrementAndGet()
                        _done.value = doneCount
                        _answers.value = results.toMap()
                        _failed.value = failedIds.toSet()
                    }
                }.forEach { it.join() }
            }
        } finally {
            _fetching.value = false
        }
    }

    companion object {
        fun factory(paper: Paper) = viewModelFactory {
            initializer { QuestionsViewModel(AppContainer.repository, paper) }
        }
    }
}
