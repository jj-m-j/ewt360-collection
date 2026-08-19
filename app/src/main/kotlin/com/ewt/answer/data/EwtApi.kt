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
 * 对应 ewt-getanwser.js / EWT-TOOL-main / opt.js 的接口逻辑。
 */
object EwtApi {

    const val BASE = "https://gateway.ewt360.com"
    const val WEB = "https://web.ewt360.com"
    /** 查看答案 */
    const val BIZ_VIEW = "201"
    /** 课后习题 */
    const val BIZ_EXERCISE = "204"
    /** 作业试卷 */
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
        DebugLog.d("API", ">> ${if (body != null) "POST" else "GET"} $url${body?.let { " body=${it.toString().take(300)}" } ?: ""}")
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
                DebugLog.e("API", "HTTP ${resp.code} for $url\n${text.take(500)}")
                throw EwtException("HTTP ${resp.code}", resp.code)
            }
            val element = try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                DebugLog.e("API", "响应解析失败 for $url\n${text.take(500)}", e)
                throw EwtException("响应解析失败", 0, e)
            }
            val obj = element as? JsonObject ?: throw EwtException("响应格式异常")
            if (obj["success"]?.jsonPrimitive?.booleanOrNull == false) {
                val msg = obj["msg"]?.jsonPrimitive?.contentOrNull ?: "接口返回失败"
                DebugLog.e("API", "业务失败 $url msg=$msg\n${text.take(800)}")
                throw EwtException(msg)
            }
            DebugLog.d("API", "<< $url\n${text.take(500)}")
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

/** 统一“data 双层嵌套”解包 */
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

/** 端点封装：与 ewt-getanwser.js / EWT-TOOL-main / opt.js 一一对应 */
object EwtEndpoints {

    // ── 用户 / 登录态 ────────────────────────────────────────────

    suspend fun getUserBaseInfo(): JsonObject =
        EwtApi.getJson("${EwtApi.WEB}/api/usercenter/user/baseinfo")

    suspend fun getSchoolUserInfo(): JsonObject =
        EwtApi.getJson("${EwtApi.BASE}/api/eteacherproduct/school/getSchoolUserInfo", EwtApi.courseHeaders())

    // ── 试卷 / 答案 ─────────────────────────────────────────────

    /** 初始化 / 获取 reportId（EWT-TOOL-main initReport） */
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

    /** 查看态 report（JS getReportId：bizCode=201） */
    suspend fun getReportIdView(paperId: String, platform: String, bizCode: String): JsonObject {
        val token = EwtApi.token ?: throw EwtException("未登录")
        val url = "${EwtApi.BASE}/api/answerprod/web/answer/report" +
            "?paperId=$paperId&platform=$platform&bizCode=$bizCode&token=$token"
        return EwtApi.getJson(url, EwtApi.commonHeaders())
    }

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

    // ── 作业 / 试卷 / 课后习题扫描（EWT-TOOL-main + opt.js） ─────

    /** 学生作业列表（status 1/2/3） */
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

    /** 作业日期/学科统计（opt.js getStudentHomeworkDaySubjectStat） */
    suspend fun getStudentHomeworkDaySubjectStat(schoolId: String, homeworkId: Long): JsonObject =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/student/homework/task/getStudentHomeworkDaySubjectStat",
            buildJsonObject {
                put("schoolId", schoolId.toLongOrNull() ?: 0L)
                put("homeworkId", homeworkId)
                put("mustLearnSubjectList", JsonArray((1..12).map { JsonPrimitive(it) }))
                put("queryMustLearn", 1)
            },
            EwtApi.courseHeaders(),
        )

    /** 任务列表（opt.js：student/homework/task/pageHomeworkTasks，按天或学科） */
    suspend fun pageHomeworkTasksOpt(schoolId: String, homeworkId: Long, dayId: String?, subjectId: Int?): JsonArray? {
        val body = buildJsonObject {
            put("schoolId", schoolId.toLongOrNull() ?: 0L)
            put("homeworkId", homeworkId)
            put("mustLearnSubjectList", JsonArray((1..12).map { JsonPrimitive(it) }))
            put("queryMustLearn", 1)
            put("pageIndex", 1)
            put("pageSize", 1000)
            if (dayId != null) put("dayId", dayId) else if (subjectId != null) put("subjectId", subjectId)
        }
        return EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/student/homework/task/pageHomeworkTasks",
            body,
            EwtApi.courseHeaders(),
        ).unwrapArray("data")
    }

    /** 查询课程课后习题（opt.js queryStudentLessonStudyGuideAndPractice） */
    suspend fun queryStudentLessonStudyGuideAndPractice(
        schoolId: String,
        lessonIdList: List<String>,
        taskIds: List<String>,
        homeworkId: Long,
    ): JsonArray? =
        EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/student/homework/task/queryStudentLessonStudyGuideAndPractice",
            buildJsonObject {
                put("schoolId", schoolId.toLongOrNull() ?: 0L)
                put("lessonIdList", JsonArray(lessonIdList.map { JsonPrimitive(it) }))
                put("taskIds", JsonArray(taskIds.map { JsonPrimitive(it) }))
                put("homeworkId", homeworkId)
            },
            EwtApi.courseHeaders(),
        ).unwrapArray("data")

    // ── 提交链路（EWT-TOOL-main paperFiller / opt.js 流程） ──────

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

    /** 提交答案（选择题答案 / 非选择题自批项；body 对齐 opt.js：含 homeworkId） */
    suspend fun submitAnswer(
        paperId: String,
        reportId: String,
        platform: String,
        bizCode: String,
        answers: JsonArray,
        homeworkId: String = "0",
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
                put("homeworkId", homeworkId)
            },
        )

    /** 交卷 */
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

    /** 自批 */
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
