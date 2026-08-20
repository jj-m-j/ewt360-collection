package com.ewt.answer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ewt.answer.data.AppContainer
import com.ewt.answer.data.CourseApi
import com.ewt.answer.data.DebugLog
import okhttp3.OkHttpClient

class EwtApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        AppContainer.init(this)
        // BFE 上报需要真实设备分辨率（699102 设备信息异常：Android 设备 + web 横向分辨率 = 矛盾）
        val dm = resources.displayMetrics
        CourseApi.deviceResolution = "${dm.widthPixels}*${dm.heightPixels}"
    }

    /** 图片请求专用 OkHttp：统一补 UA / Referer / Origin（EWT 图床无 Referer 会 403） */
    private val imageClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://web.ewt360.com/mystudy/")
                    .header("Origin", "https://web.ewt360.com")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    /** Coil3 图片加载器：使用带默认头的 OkHttp 网络加载 */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageClient })) }
            .build()
}
