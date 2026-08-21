package com.fuck.ewt

import com.chaquo.python.android.PyApplication
import android.webkit.CookieManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.fuck.ewt.data.AppContainer
import com.fuck.ewt.data.DebugLog
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

class EwtApplication : PyApplication(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        AppContainer.init(this)
    }

    /**
     * 图片请求专用 OkHttp：
     * - 统一补 UA / Referer / Origin（EWT 图床无 Referer 会 403）
     * - 接 WebView 登录 Cookie（CookieManager 全局，图床需要登录态才能加载）
     */
    private val imageClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val cookieStr = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
                    return cookieStr.split(";").mapNotNull { part ->
                        val kv = part.trim().split("=", limit = 2)
                        if (kv.size != 2 || kv[0].isBlank()) return@mapNotNull null
                        runCatching {
                            Cookie.Builder()
                                .domain(url.host)
                                .path("/")
                                .name(kv[0])
                                .value(kv[1])
                                .build()
                        }.getOrNull()
                    }
                }
            })
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

    /** Coil3 图片加载器：使用带默认头 + Cookie 的 OkHttp 网络加载（含 SVG 公式图解码） */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
                add(SvgDecoder.Factory())
            }
            .build()
}
