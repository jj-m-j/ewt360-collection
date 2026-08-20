package com.ewt.answer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 播放上报结果 */
enum class ReportResult { OK, FAIL, WAF }

/** 播放器全局会话（getPlayerGlobalConf → globalInfo） */
data class GlobalConf(
    val sessionId: String,
    val secret: String,
    val clientIp: String,
    val ts: Long,
    val diffTime: Long,
)

/**
 * 视频课（刷课）API：播放上报协议。
 * 源自 ewt360-brush（spark_ewt）逆向：BFE 播放上报 + HMAC-SHA1 签名（复刻 MSTPlayer makeSecretKey）。
 *
 * ⚠️ 699101「环境异常」最终修复（2026-08-20，termux 抓包 + 官方 app HookNext 抓包确认）：
 *  ① 协议改用 app 端 2013：monitor/app/collect/batch + videoBizCode=2013（官方 app 同款）
 *  ② body 身份完全 Android 化：os=Android、去掉 browser/browser_ver、sn=ewt_app_video_detail、
 *     UA=okhttp/4.12.0（Conscrypt 暴露 Android 设备，web 身份/脚本 UA = 身份矛盾 → 699101；
 *     官方 app Conscrypt + Android 身份 + okhttp UA 一致、termux OpenSSL + web 身份自洽）
 *  ③ URL query 完全对齐官方 app：TrLessonId + x-bfe-session-id(in URL) + TrVideoBizCode + TrUuId(纯8位hex)
 *     + TrFallback + TrUserId（去掉 sdkVersion/_ 参数）
 *  ④ 传输层：HTTP/1.1、Accept 头、Content-Type 纯 application/json
 */
object CourseApi {
    const val BFE = "https://bfe.ewt360.com"
    // 2013 = app 端协议（官方 app 同款）。web 协议 1013 + Android Conscrypt = 699101 环境异常
    const val VIDEO_BIZ = "2013"
    const val SCHOOL_VIDEO_BIZ = "1014"
    const val SDK_VERSION = "3.0.37"
    // app 端 sn（web 版是 ewt_web_video_detail，与 2013 协议不匹配）
    const val SN = "ewt_app_video_detail"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // 对齐 httpx（termux 成功）：禁 HTTP/2，强制 HTTP/1.1
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    /** HMAC-SHA1 签名（参数按 key 排序后 & 拼接，secret 来自 getPlayerGlobalConf） */
    fun makeSignature(action: Int, duration: Int, mstid: String, timestampMs: Long, secret: String): String {
        val params = mapOf(
            "action" to action.toString(),
            "duration" to duration.toString(),
            "mstid" to mstid,
            "signatureMethod" to "HMAC-SHA1",
            "signatureVersion" to "1.0",
            "timestamp" to timestampMs.toString(),
            "version" to "2022-08-02",
        )
        val signStr = params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        val mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return mac.doFinal(signStr.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    // ── 会话 / 凭证 ────────────────────────────────────────────

    suspend fun getPlayerGlobalConf(bizCode: String = VIDEO_BIZ): JsonObject {
        val url = "${EwtApi.BASE}/api/videoplayerprod/videoplayer/getPlayerGlobalConf" +
            "?videoBizCode=$bizCode&sdkVersion=$SDK_VERSION&_=${System.currentTimeMillis()}"
        return EwtApi.getJson(url)
    }

    suspend fun fetchGlobalConf(bizCode: String = VIDEO_BIZ): GlobalConf {
        val data = getPlayerGlobalConf(bizCode).optObj("data")
            ?: throw EwtException("getPlayerGlobalConf 数据为空")
        val g = data.optObj("globalInfo") ?: throw EwtException("globalInfo 为空")
        val localTs = System.currentTimeMillis()
        val ts = g.longOr("ts", 0L)
        return GlobalConf(
            sessionId = g.str("sessionId") ?: "",
            secret = g.str("secret") ?: "",
            clientIp = g.str("clientIp") ?: "",
            ts = ts,
            diffTime = ts - localTs,
        )
    }

    suspend fun getPlayerToken(schoolId: Long, lessonId: String, contentType: Int, bizCode: String = VIDEO_BIZ): String {
        val resp = EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/player/getPlayerToken",
            buildJsonObject {
                put("schoolId", schoolId)
                put("lessonId", lessonId)
                put("type", 1)
                put("contentType", contentType)
                put("videoBizCode", bizCode)
            },
        )
        val d = resp["data"]
        return when (d) {
            is JsonPrimitive -> d.contentOrNull ?: ""
            is JsonObject -> d.str("token") ?: ""
            else -> ""
        }
    }

    // ── 课时信息 / 看课检测 ────────────────────────────────────

    suspend fun getLessonInfo(schoolId: Long, homeworkId: Long, lessonId: String, contentType: Int): JsonObject {
        return EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/homework/student/getUserHomeworkLessonTaskInfo",
            buildJsonObject {
                put("schoolId", schoolId)
                put("homeworkId", homeworkId)
                put("lessonId", lessonId)
                put("contentType", contentType)
            },
        ).optObj("data") ?: throw EwtException("课时信息为空")
    }

    /** addVideoss 两步时序：scr=0 激活 → scr=2 通过 */
    suspend fun addVideoss(schoolId: Long, homeworkId: Long, lessonId: String, seriousCheckResult: Int): JsonObject {
        return EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/homework/student/addVideoss",
            buildJsonObject {
                put("schoolId", schoolId)
                put("homeworkId", homeworkId)
                put("lessonId", lessonId)
                put("type", 1)
                put("interactivePointId", JsonNull)
                put("platform", 1)
                put("seriousCheckResult", seriousCheckResult)
            },
        )
    }

    /** 查询看课检测条目（seriousCheckResult / finished / ratio），翻页遍历 */
    suspend fun pageUserVideoTaskByCondition(schoolId: Long, homeworkId: Long, pageIndex: Int): JsonObject {
        return EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/homework/student/pageUserVideoTaskByCondition",
            buildJsonObject {
                put("schoolId", schoolId.toString())
                put("pageSize", 30)
                put("missionType", 2)
                put("homeworkIds", JsonArray(listOf(JsonPrimitive(homeworkId))))
                put("pageIndex", pageIndex)
                put("queryMustLearn", true)
                put("mustLearnSubjectList", JsonArray((1..16).map { JsonPrimitive(it) }))
            },
        )
    }

    /** 查询指定课时的看课检测条目 */
    suspend fun querySeriousCheckItem(schoolId: Long, homeworkId: Long, lessonId: String): JsonObject? {
        var page = 1
        while (page <= 10) {
            val data = pageUserVideoTaskByCondition(schoolId, homeworkId, page).optObj("data")
            val items = data?.optArr("data") ?: emptyList()
            for (el in items) {
                val o = el as? JsonObject ?: continue
                val id = o.str("contentId") ?: o.str("lessonId") ?: ""
                if (id == lessonId) return o
            }
            val total = data?.longOr("totalRecords", 0L) ?: 0L
            if (items.size < 30 || page * 30 >= total) break
            page++
        }
        return null
    }

    // ── BFE 播放上报（核心） ────────────────────────────────────

    /**
     * 单条播放上报。返回 OK / FAIL / WAF。
     * action: 1=play start, 2=进度上报(竞态爆发), 3=完成
     * 走 app 端协议（2013，官方 app 同款），body 身份完全 Android 化。
     */
    suspend fun reportBatch(
        conf: GlobalConf,
        token: String,
        lessonId: String,
        courseId: String?,
        schoolId: Long,
        bizCode: String,
        videoType: Int,
        action: Int,
        stayTimeMs: Int,
        pointId: Int,
        pointNum: Int,
        uuid: String,
    ): ReportResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val reportTime = now + conf.diffTime
        // begin_time 按 action 对齐脚本（ewt_brush_v2）：
        // - action=1（play start）：report_time - 60s（脚本 _report_point 默认 begin_offset_ms=60000）
        // - action=2（进度上报）  ：conf.ts（脚本 _concurrent_burst 竞态爆发用会话 ts）
        // - action=3（播放结束）  ：report_time（脚本 begin_offset_ms=0）
        val beginTime = when (action) {
            1 -> reportTime - 60_000
            3 -> reportTime
            else -> conf.ts
        }
        // action=1 播放启动必须 speed=1（脚本 action=1 用默认 speed=1；启动即 2 倍速 = 明显非人工 → 699101）
        val speed = if (action == 1) 1 else 2
        val sig = makeSignature(action, stayTimeMs, token, reportTime, conf.secret)
        val userId = token.substringBefore("-").toLongOrNull() ?: 0L

        val eventPkg = buildJsonObject {
            put("lesson_id", lessonId)
            put("stay_time", stayTimeMs)
            put("media_time", 0)
            put("status", if (action == 3) 3 else 1)
            put("begin_time", beginTime)
            put("report_time", reportTime)
            put("point_time_id", pointId)
            put("point_time", 60000)
            put("point_num", pointNum)
            put("video_type", videoType)
            put("speed", speed)
            put("quality", "高清")
            put("action", action)
            put("fallback", 0)
            put("uuid", uuid)
            if (courseId != null) put("course_id", courseId)
        }
        val body = buildJsonObject {
            put(
                "CommonPackage",
                buildJsonObject {
                    put("userid", userId)
                    put("ip", conf.clientIp)
                    put("os", "Android")
                    put("resolution", "1920*1080")
                    put("mstid", token)
                    put("playerType", 1)
                    put("sdkVersion", SDK_VERSION)
                    put("videoBizCode", bizCode)
                    put("memberProvinceCode", "320000")
                    put("schoolId", schoolId.toString())
                    put("schoolProvinceCode", "320000")
                },
            )
            put("EventPackage", JsonArray(listOf(eventPkg)))
            put("signature", sig)
            put("sn", SN)
            put("_", System.currentTimeMillis())
        }

        // app 端协议：query 完全对齐官方 app（TrLessonId + x-bfe-session-id + TrVideoBizCode + TrUuId 纯8位 + TrFallback + TrUserId）
        val uuid8 = uuid.substringBefore("_")
        val url = "$BFE/monitor/app/collect/batch" +
            "?TrLessonId=$lessonId" +
            "&x-bfe-session-id=${conf.sessionId}" +
            "&TrVideoBizCode=$bizCode" +
            "&TrUuId=$uuid8" +
            "&TrFallback=0" +
            "&TrUserId=$userId"
        val request = Request.Builder()
            .url(url)
            .header("token", token)
            .header("x-bfe-session-id", conf.sessionId)
            .header("Accept", "*/*")
            .header("Content-Type", "application/json")
            // UA 对齐官方 app 网络栈（okhttp）：Conscrypt Android 设备 + okhttp UA 身份一致
            .header("User-Agent", "okhttp/4.12.0")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val ct = resp.headers["Content-Type"] ?: ""
                val result = if ("text/html" in ct || !text.trim().startsWith("{")) {
                    ReportResult.WAF
                } else {
                    if (resp.isSuccessful) ReportResult.OK else ReportResult.FAIL
                }
                DebugLog.d("BFE", "action=$action lesson=$lessonId http=${resp.code} ct=${ct.take(20)} result=$result body=${text.take(200)}")
                result
            }
        } catch (e: Exception) {
            DebugLog.e("BFE", "上报异常 action=$action lesson=$lessonId", e)
            ReportResult.FAIL
        }
    }
}
