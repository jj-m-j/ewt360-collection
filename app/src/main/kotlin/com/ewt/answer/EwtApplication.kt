package com.ewt.answer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ewt.answer.data.AppContainer
import com.ewt.answer.data.DebugLog

class EwtApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        AppContainer.init(this)
    }

    /** Coil3 图片加载器：使用 OkHttp 网络加载 */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
}
