package com.ewt.answer.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.random.Random

/** 视频课时（扫描结果，contentType 1=视频 / 11=校本视频） */
data class VideoLesson(
    val lessonId: String,
    val homeworkId: Long,
    val homeworkTitle: String,
    val courseId: String?,
    val title: String,
    val durationSec: Long,
    val finished: Boolean,
    val mustLearn: Boolean,
    val subjectName: String,
    val date: String,
    val contentType: Int,
)

/** 单课时刷课结果 */
data class BrushResult(val success: Boolean, val message: String)

/**
 * 视频课刷课引擎（原生实现，协议源自 ewt360-brush / spark_ewt）：
 * 扫描视频课时 → 播放上报（BFE，HMAC-SHA1 签名）→ 竞态爆发加速 → 看课检测置过。
 */
class CourseRepository {

    private var cachedSchoolId: Long = 0L

    private suspend fun schoolId(): Long {
        if (cachedSchoolId > 0) return cachedSchoolId
        val school = EwtEndpoints.getSchoolUserInfo().optObj("data")
            ?: throw EwtException("未获取到学校信息")
        cachedSchoolId = school.longOr("schoolId", 0L)
        if (cachedSchoolId == 0L) throw EwtException("schoolId 为空")
        DebugLog.d("Course", "schoolId=$cachedSchoolId")
        return cachedSchoolId
    }

    /** 作业列表（复用作业扫描逻辑） */
    private suspend fun fetchHomeworks(schoolId: Long): List<HomeworkItem> {
        val seen = mutableSetOf<Long>()
        val all = mutableListOf<HomeworkItem>()
        for (status in intArrayOf(1, 2, 3)) {
            try {
                val list = EwtEndpoints.getStudentHomeworkInfo(schoolId.toString(), status) ?: continue
                for (el in list) {
                    val obj = el as? JsonObject ?: continue
                    val id = obj.longOr("homeworkId", 0L)
                    if (id == 0L || !seen.add(id)) continue
                    all.add(
                        HomeworkItem(
                            homeworkId = id,
                            title = obj.str("title") ?: obj.str("homeworkName") ?: "作业 #$id",
                            startTime = obj.longOr("startTime", 0L),
                            endTime = obj.longOr("endTime", 0L),
                        ),
                    )
                }
            } catch (e: Exception) {
                DebugLog.e("Course", "获取作业失败 status=$status", e)
            }
        }
        all.sortByDescending { if (it.endTime != 0L) it.endTime else it.startTime }
        DebugLog.d("Course", "作业列表共 ${all.size} 个")
        return all
    }

    /** 任务列表（1..16 学科，与 spark_ewt 一致） */
    private suspend fun pageTasks(schoolId: String, homeworkId: Long, dayId: String?, subjectId: Int?): List<JsonObject> {
        val body = buildJsonObject {
            put("schoolId", schoolId.toLongOrNull() ?: 0L)
            put("homeworkId", homeworkId)
            put("mustLearnSubjectList", JsonArray((1..16).map { JsonPrimitive(it) }))
            put("queryMustLearn", 1)
            put("pageIndex", 1)
            put("pageSize", 1000)
            if (dayId != null) put("dayId", dayId) else if (subjectId != null) put("subjectId", subjectId)
        }
        val data = EwtApi.postJson(
            "${EwtApi.BASE}/api/homeworkprod/student/homework/task/pageHomeworkTasks",
            body,
            EwtApi.courseHeaders(),
        )
        return data.optObj("data")?.optArr("data")?.mapNotNull { it as? JsonObject }
            ?: data.optArr("data")?.mapNotNull { it as? JsonObject }
            ?: emptyList()
    }

    /**
     * 扫描全部作业下的视频 / 校本课时（contentType 1/11）。
     * 按日期统计分组拉取（与 spark_ewt list_video_tasks 同源）。
     */
    suspend fun scanVideoLessons(onProgress: (String) -> Unit = {}): List<VideoLesson> {
        DebugLog.d("Course", "scanVideoLessons 开始")
        val sid = schoolId()
        onProgress("正在获取作业列表…")
        val homeworks = fetchHomeworks(sid)
        if (homeworks.isEmpty()) {
            DebugLog.d("Course", "作业列表为空，直接返回")
            return emptyList()
        }
        val lessons = mutableListOf<VideoLesson>()
        val seen = mutableSetOf<String>()
        homeworks.forEachIndexed { i, hw ->
            onProgress("扫描作业 ${i + 1}/${homeworks.size}：${hw.title}")
            try {
                val ls = scanHomeworkVideoLessons(sid, hw)
                DebugLog.d("Course", "作业 ${hw.homeworkId} 视频课时数=${ls.size}")
                lessons += ls.filter { seen.add(it.lessonId) }
            } catch (e: Exception) {
                DebugLog.e("Course", "作业扫描失败 hw=${hw.homeworkId}", e)
            }
        }
        onProgress("共找到 ${lessons.size} 个视频课时")
        DebugLog.d("Course", "共找到 ${lessons.size} 个视频课时")
        return lessons
    }

    private suspend fun scanHomeworkVideoLessons(schoolId: Long, hw: HomeworkItem): List<VideoLesson> {
        val out = mutableListOf<VideoLesson>()
        val hid = hw.homeworkId

        var dateStat: List<JsonObject> = emptyList()
        try {
            val statData = EwtEndpoints.getStudentHomeworkDaySubjectStat(schoolId.toString(), hid)
                .optObj("data")
            dateStat = (statData?.optArr("dateStat") ?: statData?.optArr("dayStat")
                ?: statData?.optArr("list") ?: emptyList())
                .mapNotNull { it as? JsonObject }
                .filter { !it.str("dateId").isNullOrBlank() }
            DebugLog.d("Course", "作业 $hid 日期分组数=${dateStat.size}")
        } catch (e: Exception) {
            DebugLog.e("Course", "日期统计失败 hw=$hid", e)
        }

        val tasks: List<JsonObject> = if (dateStat.isNotEmpty()) {
            dateStat.mapNotNull { ds ->
                val dayId = ds.str("dateId")
                if (dayId.isNullOrBlank()) return@mapNotNull null
                try {
                    pageTasks(schoolId.toString(), hid, dayId, null)
                } catch (e: Exception) {
                    DebugLog.e("Course", "任务拉取失败 day=$dayId", e)
                    emptyList()
                }
            }.flatten()
        } else {
            (1..16).mapNotNull { subj ->
                try {
                    pageTasks(schoolId.toString(), hid, null, subj)
                } catch (e: Exception) {
                    emptyList()
                }
            }.flatten()
        }

        // 日志：本作业任务 contentType 分布（排查扫描不到用）
        val dist = tasks.groupBy { it.intOr("contentType", -1) }
            .map { (k, v) -> "$k=${v.size}" }
            .joinToString(", ")
        DebugLog.d("Course", "作业 $hid 任务数=${tasks.size} contentType分布[$dist]")

        val seen = mutableSetOf<String>()
        for (t in tasks) {
            val ct = t.intOr("contentType", 1)
            if (ct != 1 && ct != 11) continue
            val lid = t.str("contentId") ?: continue
            if (!seen.add(lid)) continue
            val finished = t["finished"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
                || t.boolOr("finishStatus", false)
            out.add(
                VideoLesson(
                    lessonId = lid,
                    homeworkId = hid,
                    homeworkTitle = hw.title,
                    courseId = t.str("parentContentId"),
                    title = t.str("title") ?: "未知课时",
                    durationSec = t.longOr("duration", 0L),
                    finished = finished,
                    mustLearn = t.intOr("mustLearning", 0) == 1,
                    subjectName = t.str("subjectName").orEmpty(),
                    date = "",
                    contentType = ct,
                ),
            )
        }
        return out
    }

    // ── 刷课核心 ──────────────────────────────────────────────

    /** 竞态爆发：N 路并发同时打 BFE（服务端 check-and-deduct 非原子 → 多数滑过限流） */
    private suspend fun burstFire(
        conf: GlobalConf,
        token: String,
        lesson: VideoLesson,
        schoolId: Long,
        bizCode: String,
        videoType: Int,
        nThreads: Int = 12,
    ): Pair<Int, Int> {
        val gate = CompletableDeferred<Unit>()
        val results = java.util.concurrent.ConcurrentLinkedQueue<ReportResult>()
        coroutineScope {
            val jobs = (0 until nThreads).map { i ->
                async(Dispatchers.IO) {
                    gate.await()
                    val uuid = "${java.util.UUID.randomUUID().toString().substring(0, 8)}_$i"
                    val r = runCatching {
                        CourseApi.reportBatch(
                            conf = conf,
                            token = token,
                            lessonId = lesson.lessonId,
                            courseId = lesson.courseId,
                            schoolId = schoolId,
                            bizCode = bizCode,
                            videoType = videoType,
                            action = 2,
                            stayTimeMs = 10_000,
                            pointId = 200 + i,
                            pointNum = 20,
                            beginOffsetMs = 60_000,
                            uuid = uuid,
                        )
                    }.getOrDefault(ReportResult.FAIL)
                    results.add(r)
                }
            }
            delay(30) // 就绪窗口后同时放行
            gate.complete(Unit)
            jobs.forEach { it.join() }
        }
        val ok = results.count { it == ReportResult.OK }
        val waf = results.count { it == ReportResult.WAF }
        return ok to waf
    }

    /** 单课时刷课（action=1 启动 → 竞态爆发循环 → 看课检测置过 → 确认） */
    suspend fun brushLesson(
        lesson: VideoLesson,
        onProgress: (String) -> Unit = {},
    ): BrushResult {
        val token = EwtApi.token ?: return BrushResult(false, "未登录")
        val sid = schoolId()
        val bizCode = if (lesson.contentType == 11) CourseApi.SCHOOL_VIDEO_BIZ else CourseApi.VIDEO_BIZ
        val videoType = if (lesson.contentType == 11) 6 else 1

        // Step 1: 课时信息（目标播放时长）
        val info = try {
            CourseApi.getLessonInfo(sid, lesson.homeworkId, lesson.lessonId, lesson.contentType)
        } catch (e: Exception) {
            return BrushResult(false, "获取课时信息失败：${e.message}")
        }
        val lessonTime = info.longOr("lessonTime", 0L)
        val finishPlayTime = info.longOr(
            "finishPlayTime",
            if (lessonTime > 0) (lessonTime * 0.8).toLong() else 0L,
        )
        var playTime = info.longOr("playTime", 0L)
        var needed = finishPlayTime - playTime
        if (finishPlayTime <= 0L) {
            return BrushResult(false, "课时时长异常，无法刷课")
        }
        if (needed <= 0L) {
            return BrushResult(true, "进度已达标")
        }

        // Step 2: 会话凭证
        var conf = try {
            CourseApi.fetchGlobalConf(bizCode)
        } catch (e: Exception) {
            return BrushResult(false, "获取播放配置失败：${e.message}")
        }
        runCatching {
            CourseApi.getPlayerToken(sid, lesson.lessonId, lesson.contentType, bizCode)
        }

        // Step 3: action=1 play start
        runCatching {
            CourseApi.reportBatch(
                conf, token, lesson.lessonId, lesson.courseId, sid,
                bizCode, videoType, 1, 0, 0, 20, 0,
                "${java.util.UUID.randomUUID().toString().substring(0, 8)}_start",
            )
        }
        onProgress("已启动")

        // Step 4: 竞态爆发循环（stay_time=10s，间隔约 10s ±20% 抖动）
        var stall = 0
        var round = 0
        var pct = 0.0
        while (needed > 0L && stall < 3) {
            if (!currentCoroutineContext().isActive) return BrushResult(false, "已停止")
            round++
            delay((8_000 + Random.nextLong(0, 4_000)).toLong())

            // 刷新会话（secret 可能过期）
            conf = runCatching { CourseApi.fetchGlobalConf(bizCode) }.getOrDefault(conf)

            val (ok, waf) = burstFire(conf, token, lesson, sid, bizCode, videoType)
            if (waf > 0) {
                onProgress("风控拦截，冷却中…")
                delay(120_000)
                continue
            }

            // 查询进度
            val info2 = runCatching {
                CourseApi.getLessonInfo(sid, lesson.homeworkId, lesson.lessonId, lesson.contentType)
            }.getOrNull()
            if (info2 != null) {
                val newPlay = info2.longOr("playTime", playTime)
                val delta = newPlay - playTime
                playTime = newPlay
                needed = (finishPlayTime - playTime).coerceAtLeast(0L)
                pct = info2.doubleOr("percent", pct)
                onProgress("第 $round 轮：${(pct * 100).toInt()}%（成功 $ok/${"12"}）")
                if (delta > 0L) {
                    stall = 0
                    continue
                }
            }

            // 停滞恢复：addVideoss 两步时序（scr=0 激活 → scr=2 通过）
            onProgress("停滞检测，处理中…")
            runCatching {
                CourseApi.addVideoss(sid, lesson.homeworkId, lesson.lessonId, 0)
                delay(3_000 + Random.nextLong(0, 2_000))
                CourseApi.addVideoss(sid, lesson.homeworkId, lesson.lessonId, 2)
            }
            delay(2_000)
            burstFire(conf, token, lesson, sid, bizCode, videoType)
            val info3 = runCatching {
                CourseApi.getLessonInfo(sid, lesson.homeworkId, lesson.lessonId, lesson.contentType)
            }.getOrNull()
            if (info3 != null) {
                val newPlay = info3.longOr("playTime", playTime)
                val d2 = newPlay - playTime
                playTime = newPlay
                needed = (finishPlayTime - playTime).coerceAtLeast(0L)
                pct = info3.doubleOr("percent", pct)
                if (d2 > 0L) {
                    stall = 0
                    onProgress("第 $round 轮：${(pct * 100).toInt()}%（检测已通过）")
                    continue
                }
            }
            stall++
            onProgress("第 $round 轮：进度未增长（$stall/3）")
        }

        // Step 5: 完成确认 + 看课检测置过
        runCatching {
            CourseApi.addVideoss(sid, lesson.homeworkId, lesson.lessonId, 0)
            delay(3_000)
            CourseApi.addVideoss(sid, lesson.homeworkId, lesson.lessonId, 2)
        }
        val passed = checkPassed(sid, lesson)
        onProgress(if (passed) "已完成 ${(pct * 100).toInt()}%" else "进度 ${(pct * 100).toInt()}%")
        return BrushResult(
            success = passed || needed <= 0L,
            message = if (passed) "已完成" else "进度 ${(pct * 100).toInt()}%（未确认通过）",
        )
    }

    /** 确认课时是否已完成（finished / ratio>=1 / percent>=1） */
    suspend fun checkPassed(schoolId: Long, lesson: VideoLesson): Boolean {
        try {
            val item = CourseApi.querySeriousCheckItem(schoolId, lesson.homeworkId, lesson.lessonId)
            if (item != null) {
                if (item.boolOr("finished", false) || (item.doubleOr("ratio", 0.0) >= 1.0)) return true
                if (item.intOr("seriousCheckResult", 0) >= 2) return true
            }
        } catch (e: Exception) {
            DebugLog.e("Course", "检测查询失败", e)
        }
        return try {
            val info = CourseApi.getLessonInfo(schoolId, lesson.homeworkId, lesson.lessonId, lesson.contentType)
            info.doubleOr("percent", 0.0) >= 1.0
        } catch (e: Exception) {
            false
        }
    }
}
