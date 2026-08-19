package com.ewt.answer.data

import android.content.Context

/** 轻量依赖容器 */
object AppContainer {

    lateinit var tokenStore: SecureTokenStore
        private set
    lateinit var repository: EwtRepository
        private set

    fun init(context: Context) {
        if (::repository.isInitialized) return
        tokenStore = SecureTokenStore(context.applicationContext)
        repository = EwtRepository(tokenStore)
        repository.restoreToken()
    }
}
