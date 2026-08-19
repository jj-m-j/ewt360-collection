package com.ewt.answer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** EWT API 业务错误 */
class EwtException(message: String, val code: Int = 0, cause: Throwable? = null) : Exception(message, cause)

/**
 * EWT360 网关传输层
 *
 * 对应 ewt-getanwser.js 的 BASE / 请求头 / UA 逻辑，
 * 与 EWT-TOOL-main 的 ewtApi.ts（CommonHeader / CourseHeader）逻辑。
 */
object EwtApi {

    const val BASE = "https://gateway.ewt360.com"
    const val WEB = "https://web.ewt360.com"
    /** 查看答案使用的 bizCode（JS 中 BIZ_VIEW） */
    const val BIZ_VIEW = "201"
    /** 作业答题/提交使用的 bizCode（EWT-TOOL-main 使用） */
    const val BIZ_SUBMIT = "205"
    const val PLATFORM = "1"
    const val UA = "Mozilla/5.0"

    /** 当前登录 token（内存态，持久化见 SecureTokenStore） */
    @Volatile
    var token: String? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** web 端公共请求头（对应 ewtApi.ts COMMON_HEADERS + token） */
    fun commonHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> = buildMap {
        putAll(extra)
        put("Accept", "application/json, text/plain, */*")
        put("Content-Type", "application/json; charset=UTF-8")
        put("Origin", "https://web.ewt360.com")
        put("Referer", "https://web.ewt360.com/mystudy/")
        put("Ewt-Requestsource", "web")
        put("Ewt-Contentstyle", "CamelCase")
        put("User-Agent", UA)
        token?.let { put("token", it) }
    }

    /** 课程侧请求头（对应 ewtApi.ts COURSE_HEADERS，作业/试卷接口使用） */
    fun courseHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> = buildMap {
        putAll(extra)
        put("Accept", "application/json, text/plain, */*")
        put("Content-Type", "application/json; charset=UTF-8")
        put("Origin", "https://teacher.ewt360.com")
        put("Referer", "https://teacher.ewt360.com/")
        put("Ewt-Requestsource", "web")
        put("Ewt-Contentstyle", "CamelCase")
        put("User-Agent", UA)
        token?.let { put("token", it) }
    }

    suspend fun getJson(
        url: String,
        headers: Map<String, String> = commonHeaders(),
    ): JsonObject = withContext(Dispatchers.IO) { execute(url, headers, null) }

    suspend fun postJson(
        url: String,
        body: JsonObject,
        headers: Map<String, String> = commonHeaders(),
    ): JsonObject = withContext(Dispatchers.IO) { execute(url, headers, body) }

    private fun execute(url: String, headers: Map<String, String>, body: JsonObject?): JsonObject {
        val builder = Request.Builder()
            .url(url)
            .headers(
                Headers.Builder().apply {
                    headers.forEach { (k, v) -> add(k, v) }
                }.build(),
            )
        if (body != null) {
            builder.post(body.toString().toRequestBody(JSON_MEDIA))
        } else {
            builder.get()
        }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw EwtException("HTTP ${resp.code}", resp.code)
            }
            val element = try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw EwtException("响应解析失败", 0, e)
            }
            val obj = element as? JsonObject ?: throw EwtException("响应格式异常")
            if (obj["success"]?.jsonPrimitive?.booleanOrNull == false) {
                val msg = obj["msg"]?.jsonPrimitive?.contentOrNull ?: "接口返回失败"
                throw EwtException(msg)
            }
            return obj
        }
    }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
}

// ── JsonObject 便捷导航（容忍 API 结构变化） ─────────────────────

fun JsonObject.optObj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.optArr(key: String): JsonArray? = this[key] as? JsonArray
fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

fun JsonObject.str(key: String): String? = this[key]?.str()
fun JsonObject.strOr(key: String, def: String): String = this.str(key) ?: def
fun JsonObject.intOr(key: String, def: Int): Int =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: def
fun JsonObject.longOr(key: String, def: Long): Long =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: def
fun JsonObject.boolOr(key: String, def: Boolean): Boolean =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: def
fun JsonObject.doubleOr(key: String, def: Double): Double =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: def

/**
 * 统一“data 双层嵌套”解包：
 * { success, data: [...] } / { success, data: { data: [...] } } / { success, data: { list: [...] } }
 */
fun JsonObject.unwrapArray(key: String): JsonArray? {
    val data = optObj("data")
    if (data != null) {
        data.optArr(key)?.let { return it }
        data.optArr("data")?.let { return it }
        data.optArr("list")?.let { return it }
        return null
    }
    return optArr("data")
}

/** 端点封装：与 ewt-getanwser.js / EWT-TOOL-main 一一对应 */
object EwtEndpoints {

    // ── 用户 / 登录态 ────────────────────────────────────────────

    /** 用户基础信息（含 userId / realName）；失败即登录态失效 */
    suspend fun getUserBaseInfo(): JsonObject =
        EwtApi.getJson("${EwtApi.WEB}/api/usercenter/user/baseinfo")

    /** 学校信息（课程侧）→ schoolId */
    suspend fun getSchoolUserInfo(): JsonObject =
        EwtApi.getJson("${EwtApi.BASE}/api/eteacherproduct/school/getSchoolUserInfo", EwtApi.courseHeaders())

    // ── 试卷 / 答案 ─────────────────────────────────────────────

    /**
     * 初始化 / 获取 reportId（EWT-TOOL-main initReport）：
     * bizCode=205 + extId(homeworkId) + reportId=0 + isRepeat，
     * 未做过的试卷也能初始化；已做过的试卷在 isRepeat=0 失败后改用 isRepeat=1。
     */
    suspend fun initReport(
        paperId: String,
        platform: String,
        bizCode: String,
        extId: Long = 0,
        isRepeat: Int = 0,
    ): JsonObject {
        val token = EwtApi.token ?: throw EwtException("未登录")
        val url = "${EwtApi.BASE}/api/answerprod/web/answer/report" +
            "?paperId=$paperId&platform=$platform&extId=$extId&bizCode=$bizCode&reportId=0&isRepeat=$isRepeat&homeworkId=$extId&token=$token"
        return EwtApi.getJson(url, EwtApi.commonHeaders())
    }

    /** 题组试卷题目（JS getAnswerSheetSubGroup） */
    suspend fun getAnswerSheetSubGroup(paperId: String, reportId: String, platform: String, bizCode: String): JsonObject =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/answerprod/common/answer/sheet/getAnswerSheetSubGroup",
            buildJsonObject {
                put("paperId", paperId)
                put("reportId", reportId)
                put("platform", platform)
                put("bizCode", bizCode)
                put("homeworkId", "0")
                put("client", 4)
            },
        )

    /** 非题组试卷题目（JS 回退 answerSheetInfo，需要 userId） */
    suspend fun getAnswerSheetInfo(paperId: String, reportId: String, platform: String, bizCode: String, userId: String): JsonObject =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/answerprod/common/answer/answerSheetInfo",
            buildJsonObject {
                put("paperId", paperId)
                put("reportId", reportId)
                put("platform", platform)
                put("bizCode", bizCode)
                put("userId", userId)
                put("client", 1)
            },
        )

    /** 单题答案 / 解析 / 知识点 / 图片（JS getAnswer） */
    suspend fun getQuestionAnalysis(
        paperId: String,
        reportId: String,
        platform: String,
        questionId: String,
        bizCode: String,
    ): JsonObject =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/answerprod/web/answer/simple/question/analysis",
            buildJsonObject {
                put("paperId", paperId)
                put("reportId", reportId)
                put("platform", platform)
                put("questionId", questionId)
                put("bizCode", bizCode)
                put("homeworkId", "0")
                put("client", 4)
            },
        )

    // ── 作业 / 试卷扫描（EWT-TOOL-main paperScanner.ts） ────────

    /** 学生作业列表（status 1/2/3 轮询后合并去重） */
    suspend fun getStudentHomeworkInfo(schoolId: String, status: Int): JsonArray? =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/homework/student/getStudentHomeworkInfo",
            buildJsonObject {
                put("schoolId", schoolId.toLongOrNull() ?: 0L)
                put("subject", JsonNull)
                put("type", JsonNull)
                put("status", status)
                put("pageIndex", 1)
                put("pageSize", 100)
                put("notClassSetting", 0)
            },
            EwtApi.courseHeaders(),
        ).unwrapArray("data")

    /** 作业天数分布（用于按天拉取任务） */
    suspend fun studentHomeworkDistribution(homeworkIds: List<Long>, schoolId: String): JsonObject =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/homework/student/studentHomeworkDistribution",
            buildJsonObject {
                put("homeworkIds", JsonArray(homeworkIds.map { JsonPrimitive(it) }))
                put("sceneId", 0)
                put("taskDistributionTypeEnum", 1)
                put("schoolId", schoolId.toLongOrNull() ?: 0L)
            },
            EwtApi.courseHeaders(),
        )

    /** 某天任务列表（含试卷 contentTypeName） */
    suspend fun pageHomeworkTasks(homeworkId: Long, dayId: String, day: Long, schoolId: String): JsonArray? =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/homework/student/pageHomeworkTasks",
            buildJsonObject {
                put("homeworkIds", JsonArray(listOf(JsonPrimitive(homeworkId))))
                put("sceneId", 0)
                put("dayId", JsonArray(listOf(JsonPrimitive(dayId))))
                put("day", day)
                put("pageIndex", 1)
                put("pageSize", 1000)
                put("schoolId", schoolId.toLongOrNull() ?: 0L)
            },
            EwtApi.courseHeaders(),
        ).unwrapArray("data")

    // ── 提交链路（用户授权；与 EWT-TOOL-main paperFiller 一致） ──

    /** 上报作答时长 / 空交卷（JS updateReport，解锁答案用） */
    suspend fun updateReport(paperId: String, reportId: String, platform: String, bizCode: String): JsonObject =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/answerprod/web/answer/submitpaper",
            buildJsonObject {
                put("paperId", paperId)
                put("reportId", reportId)
                put("bizCode", bizCode)
                put("platform", platform)
                put("totalSeconds", 600)
                put("homeworkId", "0")
            },
        )

    /** 提交答案（选择题答案 / 非选择题自批项；EWT-TOOL-main submitAnswers） */
    suspend fun submitAnswer(
        paperId: String,
        reportId: String,
        platform: String,
        bizCode: String,
        answers: JsonArray,
    ): JsonObject =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/answerprod/web/answer/submitAnswer",
            buildJsonObject {
                put("answers", answers)
                put("assignPoints", true)
                put("bizCode", bizCode)
                put("paperId", paperId)
                put("platform", platform)
                put("reportId", reportId)
            },
        )

    /** 交卷（EWT-TOOL-main submitPaper） */
    suspend fun submitPaper(paperId: String, reportId: String, platform: String, bizCode: String): JsonObject =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/answerprod/web/answer/submitpaper",
            buildJsonObject {
                put("paperId", paperId)
                put("platform", platform)
                put("reportId", reportId)
                put("totalSeconds", 600)
                put("bizCode", bizCode)
                put("homeworkId", "0")
            },
        )

    /** 自批（EWT-TOOL-main submitCorrected） */
    suspend fun submitCorrected(paperId: String, reportId: String, platform: String, bizCode: String): JsonObject =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/answerprod/web/answer/submitCorrected",
            buildJsonObject {
                put("reportId", reportId)
                put("paperId", paperId)
                put("platform", platform)
                put("bizCode", bizCode)
                put("paperPackageId", JsonNull)
            },
        )
}
