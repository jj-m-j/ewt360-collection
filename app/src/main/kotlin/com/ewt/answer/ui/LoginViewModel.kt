package com.ewt.answer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ewt.answer.data.AppContainer
import com.ewt.answer.data.EwtRepository
import com.ewt.answer.data.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 登录页 ViewModel
 *
 * 流程：WebView 中用户完成 EWT360 登录
 *   → CookieManager 检测到 token Cookie
 *   → 调 baseinfo 校验 token 有效性
 *   → 通过后加密保存登录态并进入主页
 */
class LoginViewModel(private val repo: EwtRepository) : ViewModel() {

    sealed interface Status {
        /** 等待用户在 WebView 中登录 */
        data object Waiting : Status
        /** 检测到 token，正在校验 */
        data object Validating : Status
        /** token 无效，继续等待重新登录 */
        data object Invalid : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Waiting)
    val status: StateFlow<Status> = _status

    private var accepted = false
    private var validating = false

    @Synchronized
    fun isAccepted(): Boolean = accepted

    /** 检测到 token 后调用（幂等，防并发重复校验） */
    fun onTokenDetected(token: String, onSuccess: (UserInfo) -> Unit) {
        if (accepted || validating) return
        validating = true
        viewModelScope.launch {
            _status.value = Status.Validating
            try {
                repo.saveToken(token)
                val user = repo.fetchUserInfo()
                if (user.userId.isBlank()) {
                    throw IllegalStateException("userId 为空")
                }
                accepted = true
                onSuccess(user)
            } catch (e: Exception) {
                repo.clearToken()
                _status.value = Status.Invalid
            } finally {
                validating = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LoginViewModel(AppContainer.repository) }
        }
    }
}
