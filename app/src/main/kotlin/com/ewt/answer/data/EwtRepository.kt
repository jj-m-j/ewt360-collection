package com.ewt.answer.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 仓库层：组合 EWT-TOOL-main（扫描/刷卷）、ewt-getanwser.js（答案）、opt.js（课后习题扫描 + 混合题型）能力。
 * 支持作业试卷（205）与课后习题（204）。
 */
class EwtRepository(private val tokenStore: SecureTokenStore) {

    fun restoreToken(): Boolean {
        val t = tokenStore.load()
        if (t.isNullOrBlank()) return false
        EwtApi.token = t
        return true
    }

    fun saveToken(token: String) {
        tokenStore.save(token)
        EwtApi.token = token
    }

    fun clearToken() {
        tokenStore.clear()
        EwtApi.token = null
    }

    fun hasToken(): Boolean = !EwtApi.token.isNullOrBlank()

    private fun normalizeImg(url: String): String =
        url.replace(Regex("""^http://file\.ewt360\.com/"""), "https://file.ewt360.com/")

    // ── 用户 / 登录态 ───────────────────────────────────────────

    suspend fun fetchUserInfo(): UserInfo {
        val base = EwtEndpoints.getUserBaseInfo().optObj("data")
            ?: throw EwtException("登录已失效，请重新登录")
        val school = runCatching { EwtEndpoints.getSchoolUserInfo().optObj("data") }.getOrNull()
        return UserInfo(
            userId = base.strOr("userId", ""),
            realName = base.str("realName")?.takeIf { it.isNotBlank() }
                ?: base.str("nickName").orEmpty(),
            schoolId = school?.strOr("schoolId", "") ?: "",
        )
    }

    // ── 作业 / 任务扫描（EWT-TOOL-main + opt.js） ────────────────

    suspend fun fetchHomeworks(schoolId: String): List<HomeworkItem> {
        val seen = mutableSetOf<Long>()
        val all = mutableListOf<HomeworkItem>()
        for (status in intArrayOf(1, 2, 3)) {
            try {
                val list = EwtEndpoints.getStudentHomeworkInfo(schoolId, status) ?: continue
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
                DebugLog.e("Scan", "获取作业失败 status=$status", e)
            }
        }
        all.sortByDescending { if (it.endTime != 0L) it.endTime else it.startTime }
        return all
    }

    private data class DaySlot(val dayId: String?, val subjectId: Int?, val date: Long)

    /**
     * 扫描单个作业下所有可答题任务（试卷 205 + 课后习题 204），按日期正序。
     * 日期统计失败时回退按 12 学科扫描，日期用作业开始时间（布置日近似）兜底。
     */
    suspend fun scanHomeworkPapers(schoolId: String, homework: HomeworkItem): List<Paper> {
        val papers = mutableListOf<Paper>()
        val hid = homework.homeworkId

        var dayList = mutableListOf<DaySlot>()
        try {
            val statData = EwtEndpoints.getStudentHomeworkDaySubjectStat(schoolId, hid).optObj("data")
            val days = statData?.optArr("dateStat") ?: statData?.optArr("dayStat")
                ?: statData?.optArr("days") ?: statData?.optArr("list")
            for (el in days ?: emptyList()) {
                val o = el as? JsonObject ?: continue
                val dayId = o.str("dateId") ?: o.str("dayId")
                if (dayId != null && dayId != "0") {
                    dayList.add(DaySlot(dayId, null, o.longOr("date", 0L)))
                } else {
                    val sid = o.intOr("subjectId", 0)
                    if (sid > 0) dayList.add(DaySlot(null, sid, 0L))
                }
            }
        } catch (e: Exception) {
            DebugLog.e("Scan", "日期统计失败，回退按学科", e)
        }
        val fallbackDate = if (dayList.isEmpty()) {
            formatDate(homework.startTime)
        } else {
            ""
        }
        if (dayList.isEmpty()) {
            dayList = (1..12).map { DaySlot(null, it, 0L) }.toMutableList()
        }
        dayList.sortBy { it.date }

        for (slot in dayList) {
            try {
                val tasks = EwtEndpoints.pageHomeworkTasksOpt(schoolId, hid, slot.dayId, slot.subjectId) ?: continue
                val dateStr = if (slot.date > 0) formatDate(slot.date) else fallbackDate
                val lessonIdList = mutableListOf<String>()
                val taskIds = mutableListOf<String>()
                val taskMap = mutableMapOf<String, JsonObject>()

                for (t in tasks) {
                    val task = t as? JsonObject ?: continue
                    val typeName = task.str("contentTypeName").orEmpty()
                    val contentType = task.intOr("contentType", 0)
                    val contentTypeCode = task.intOr("contentTypeCode", 0)
                    val isPaper = contentType == 2 || typeName.contains("试卷") || contentTypeCode == 205
                    if (isPaper) {
                        val pid = task.str("contentId")
                        if (pid.isNullOrBlank() || pid == "0") continue
                        papers.add(
                            Paper(
                                homeworkId = hid,
                                homeworkTitle = homework.title,
                                paperId = pid,
                                title = task.str("title") ?: "未知试卷",
                                questionCount = task.str("questionCount") ?: task.str("itemCount") ?: "?",
                                ratio = task.doubleOr("ratio", 0.0).takeIf { it > 0 },
                                date = dateStr,
                                subjectName = task.str("subjectName").orEmpty(),
                                bizCode = EwtApi.BIZ_SUBMIT,
                            ),
                        )
                    } else {
                        val contentId = task.str("contentId")
                        val taskId = task.str("taskId")
                        if (!contentId.isNullOrBlank() && contentId != "0" && contentId != "null") {
                            lessonIdList.add(contentId)
                            taskIds.add(taskId ?: "")
                            taskMap[contentId] = task
                        }
                    }
                }

                if (lessonIdList.isNotEmpty()) {
                    try {
                        val studyData = EwtEndpoints.queryStudentLessonStudyGuideAndPractice(
                            schoolId, lessonIdList, taskIds, hid,
                        ) ?: emptyList()
                        for (item in studyData) {
                            val o = item as? JsonObject ?: continue
                            val studyTest = o.optObj("studyTest") ?: continue
                            val pid = studyTest.str("paperId") ?: continue
                            val biz = studyTest.str("bizCode") ?: EwtApi.BIZ_EXERCISE
                            val task = taskMap[o.str("lessonId")]
                            papers.add(
                                Paper(
                                    homeworkId = hid,
                                    homeworkTitle = homework.title,
                                    paperId = pid,
                                    title = task?.str("title") ?: "课程练习",
                                    questionCount = studyTest.str("questionCount") ?: "?",
                                    ratio = null,
                                    date = dateStr,
                                    subjectName = task?.str("subjectName").orEmpty(),
                                    bizCode = biz,
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        DebugLog.e("Scan", "课后习题查询失败", e)
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("Scan", "任务拉取失败 day=${slot.dayId} subj=${slot.subjectId}", e)
            }
        }
        return papers
    }

    suspend fun scanAllPapers(onProgress: (String) -> Unit = {}): List<HomeworkGroup> {
        val user = fetchUserInfo()
        if (user.schoolId.isBlank()) throw EwtException("未获取到学校信息")
        onProgress("正在获取作业列表…")
        val homeworks = fetchHomeworks(user.schoolId)
        if (homeworks.isEmpty()) return emptyList()
        val groups = mutableListOf<HomeworkGroup>()
        homeworks.forEachIndexed { i, hw ->
            onProgress("扫描作业 ${i + 1}/${homeworks.size}：${hw.title}")
            val papers = scanHomeworkPapers(user.schoolId, hw)
            if (papers.isNotEmpty()) {
                groups.add(HomeworkGroup(hw, papers))
            }
        }
        return groups
    }

    private fun formatDate(ts: Long): String {
        if (ts <= 0) return ""
        return try {
            val d = java.util.Date(ts)
            String.format("%02d-%02d", d.month + 1, d.date)
        } catch (e: Exception) {
            ""
        }
    }

    // ── 试卷会话 / 题目 ─────────────────────────────────────────

    suspend fun openPaper(paper: Paper): PaperSession {
        val (reportId, bizCode, count) = initReportId(paper)
        return PaperSession(
            paperId = paper.paperId,
            platform = EwtApi.PLATFORM,
            bizCode = bizCode,
            reportId = reportId,
            title = paper.title,
            questionCount = count,
        )
    }

    private suspend fun initReportId(paper: Paper): Triple<String, String, Int> {
        val extId = paper.homeworkId
        val biz = paper.bizCode
        val attempts = listOf(
            Triple("$biz+ext$extId+rep0", biz, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, biz, extId, 0) }),
            Triple("$biz+ext$extId+rep1", biz, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, biz, extId, 1) }),
            Triple("$biz+noext", biz, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, biz, 0, 0) }),
            Triple("205+ext$extId+rep0", EwtApi.BIZ_SUBMIT, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, EwtApi.BIZ_SUBMIT, extId, 0) }),
            Triple("201+view", EwtApi.BIZ_VIEW, suspend { EwtEndpoints.getReportIdView(paper.paperId, EwtApi.PLATFORM, EwtApi.BIZ_VIEW) }),
        )
        for ((label, b, call) in attempts) {
            val result = runCatching { call().optObj("data") }
                .onFailure { e -> DebugLog.e("Init", "候选[$label] 失败 paperId=${paper.paperId}", e) }
                .getOrNull()
            if (result != null) {
                val id = result.str("reportId") ?: result.str("report") ?: result.str("id")
                if (id != null) {
                    val count = result.intOr("questionCount", 0)
                    return Triple(id, b, count)
                }
            }
        }
        throw EwtException("初始化答卷失败：无 reportId（已尝试 5 种方式，详见日志）")
    }

    private suspend fun initSubmitReportId(paper: Paper, biz: String): String {
        val extId = paper.homeworkId
        for (isRepeat in listOf(0, 1)) {
            val id = runCatching {
                EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, biz, extId, isRepeat)
                    .optObj("data")?.let { it.str("reportId") ?: it.str("report") ?: it.str("id") }
            }.onFailure { e -> DebugLog.e("Submit", "提交 report 初始化失败 biz=$biz isRepeat=$isRepeat", e) }
                .getOrNull()
            if (id != null) return id
        }
        throw EwtException("初始化提交答卷失败：无 reportId")
    }

    suspend fun unlockPaper(session: PaperSession) {
        EwtEndpoints.updateReport(session.paperId, session.reportId, session.platform, session.bizCode)
    }

    /** 每道题分值：score / fullScore 兜底（opt.js: score || fullScore || 0） */
    private fun extractScore(o: JsonObject): Double =
        o.doubleOr("score", 0.0).takeIf { it > 0 } ?: o.doubleOr("fullScore", 0.0)

    suspend fun fetchQuestions(session: PaperSession): List<QuestionItem> {
        return try {
            fetchGroupedQuestions(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fetchFlatQuestions(session)
        }
    }

    private suspend fun fetchGroupedQuestions(session: PaperSession): List<QuestionItem> {
        val data = EwtEndpoints.getAnswerSheetSubGroup(
            session.paperId, session.reportId, session.platform, session.bizCode,
        ).optObj("data") ?: throw EwtException("题组数据为空")
        val list = data.optArr("groupQuestionList") ?: throw EwtException("题组数据为空")
        val questions = mutableListOf<QuestionItem>()
        var seq = 0
        for (g in list) {
            val group = g as? JsonObject ?: continue
            val groupName = group.str("groupName").orEmpty()
            for (q in group.optArr("questionList") ?: emptyList()) {
                val qo = q as? JsonObject ?: continue
                val qid = qo.str("questionId") ?: continue
                seq++
                val rawNo = qo.strOr("questionNumber", "").trim()
                val displayNo = if (rawNo.isEmpty() || rawNo == "0") seq.toString() else rawNo
                questions.add(
                    QuestionItem(
                        questionId = qid,
                        questionNumber = displayNo,
                        questionType = qo.str("questionType").orEmpty(),
                        cateId = qo.intOr("cateId", 1),
                        subjective = qo.boolOr("subjective", false),
                        groupName = groupName,
                        score = extractScore(qo),
                    ),
                )
            }
        }
        return questions
    }

    private suspend fun fetchFlatQuestions(session: PaperSession): List<QuestionItem> {
        val base = EwtEndpoints.getUserBaseInfo().optObj("data")
        val userId = base?.str("userId") ?: throw EwtException("获取用户信息失败")
        val data = EwtEndpoints.getAnswerSheetInfo(
            session.paperId, session.reportId, session.platform, session.bizCode, userId,
        ).optObj("data") ?: throw EwtException("题目数据为空")
        val list = data.optArr("questionInfoList") ?: throw EwtException("题目数据为空")
        val questions = mutableListOf<QuestionItem>()
        var seq = 0
        for (el in list) {
            val qo = el as? JsonObject ?: continue
            val qid = qo.str("questionId") ?: continue
            seq++
            val rawNo = qo.strOr("questionNumber", "").trim()
            val displayNo = if (rawNo.isEmpty() || rawNo == "0") seq.toString() else rawNo
            questions.add(
                QuestionItem(
                    questionId = qid,
                    questionNumber = displayNo,
                    questionType = qo.str("questionType").orEmpty(),
                    cateId = qo.intOr("cateId", 1),
                    subjective = qo.boolOr("subjective", false),
                    groupName = "",
                    score = extractScore(qo),
                ),
            )
        }
        return questions
    }

    // ── 答案获取（ewt-getanwser.js + opt.js 混合题型） ───────────

    suspend fun fetchAnswer(session: PaperSession, question: QuestionItem): QuestionAnswer? {
        return try {
            val data = EwtEndpoints.getQuestionAnalysis(
                session.paperId, session.reportId, session.platform, question.questionId, session.bizCode,
            ).optObj("data") ?: return null

            var answerStr = extractAnswerText(data)
            var analysisHtml = extractAnalysisHtml(data)
            val choiceAnswers = extractChoiceList(data).toMutableList()
            val childItems = mutableListOf<ChildAnswer>()

            val childQs = data.optArr("childQuestions") ?: emptyList()
            if (childQs.isNotEmpty()) {
                childQs.forEachIndexed { idx, c ->
                    val co = c as? JsonObject ?: return@forEachIndexed
                    val childRight = co.optArr("rightAnswer")?.mapNotNull { it.str() } ?: emptyList()
                    val opts = HtmlCleaner.extractChoiceAnswers(childRight)
                    val ans = when {
                        opts.isNotEmpty() -> opts.joinToString(", ")
                        childRight.isNotEmpty() -> childRight.map { HtmlCleaner.clean(it) }.joinToString("; ")
                        else -> "(主观题)"
                    }
                    childItems.add(
                        ChildAnswer(
                            num = "(${idx + 1})",
                            answer = ans,
                            analysisHtml = co.str("analyse").orEmpty(),
                            knowledge = co.str("knowledgeTitle").orEmpty(),
                            images = co.optArr("attachmentImages")
                                ?.mapNotNull { it.str() }
                                ?.filter { it.startsWith("http") }
                                ?.map { normalizeImg(it) }
                                ?: emptyList(),
                        ),
                    )
                }
                if (answerStr.isBlank()) {
                    answerStr = childItems.joinToString("\n") { "${it.num} ${it.answer}" }
                }
                if (analysisHtml.isBlank()) {
                    analysisHtml = childItems.joinToString("\n") { "${it.num} ${it.analysisHtml}" }
                        .takeIf { it.isNotBlank() } ?: ""
                }
            }

            val knowledges = data.optArr("knowledges")
                ?.mapNotNull { (it as? JsonObject)?.str("title") }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val images = data.optArr("attachmentImages")
                ?.mapNotNull { it.str() }
                ?.filter { it.startsWith("http") }
                ?.map { normalizeImg(it) }
                ?: emptyList()

            QuestionAnswer(
                question = question,
                answer = answerStr,
                analysisHtml = analysisHtml,
                knowledges = knowledges,
                attachmentImages = images,
                choiceAnswers = choiceAnswers,
                childItems = childItems,
                rawJson = data.toString(),
            )
        } catch (e: Exception) {
            DebugLog.e("Ans", "获取答案异常 q=${question.questionId}", e)
            null
        }
    }

    // ── 提交（用户确认后调用；EWT-TOOL-main paperFiller / opt.js 流程） ──

    /**
     * 提交整卷答案并交卷自批：客观题提交标准答案（系统阅卷），主观题 revision=true 满分自批；
     * 交卷自批后尽力查询本次得分并附加到结果文案。
     */
    suspend fun submitPaperAnswers(
        paper: Paper,
        questions: List<QuestionItem>,
        answers: Map<String, QuestionAnswer>,
    ): String {
        val biz = paper.bizCode
        val reportId = initSubmitReportId(paper, biz)

        val sel = mutableListOf<JsonObject>()
        val notSel = mutableListOf<JsonObject>()
        var objectiveCount = 0
        var subjectiveCount = 0
        for (q in questions) {
            val a = answers[q.questionId] ?: continue
            val opts = a.choiceAnswers
            if (opts.isNotEmpty() && opts.all { Regex("^[A-Z]+$").matches(it) }) {
                objectiveCount++
                sel.add(
                    buildJsonObject {
                        put("questionId", q.questionId)
                        put("questionNo", q.questionNumber.toIntOrNull() ?: 0)
                        put("totalSeconds", 50)
                        put("cateId", q.cateId)
                        put("answers", JsonArray(opts.map { JsonPrimitive(it) }))
                    },
                )
            } else {
                subjectiveCount++
                notSel.add(
                    buildJsonObject {
                        put("questionId", q.questionId)
                        put("questionNo", q.questionNumber.toIntOrNull() ?: 0)
                        put("totalSeconds", 50)
                        put("cateId", q.cateId)
                        put("answers", JsonArray(listOf(JsonPrimitive(1))))
                        put("attachmentImages", JsonArray(emptyList()))
                        put("score", q.score)
                        put("revision", true)
                    },
                )
            }
        }
        if (sel.isEmpty() && notSel.isEmpty()) {
            throw EwtException("未获取到任何有效答案，已禁止提交空卷")
        }
        if (sel.isNotEmpty()) {
            EwtEndpoints.submitAnswer(paper.paperId, reportId, EwtApi.PLATFORM, biz, JsonArray(sel), paper.homeworkId.toString())
        }
        if (notSel.isNotEmpty()) {
            EwtEndpoints.submitAnswer(paper.paperId, reportId, EwtApi.PLATFORM, biz, JsonArray(notSel), paper.homeworkId.toString())
        }
        EwtEndpoints.submitPaper(paper.paperId, reportId, EwtApi.PLATFORM, biz)
        EwtEndpoints.submitCorrected(paper.paperId, reportId, EwtApi.PLATFORM, biz)

        val scoreText = runCatching {
            val base = EwtEndpoints.getUserBaseInfo().optObj("data")
            val userId = base?.str("userId").orEmpty()
            val res = EwtEndpoints.getAnswerSheetInfo(paper.paperId, reportId, EwtApi.PLATFORM, biz, userId)
            res.optObj("data")?.extractScoreText()
        }.getOrNull()

        return "已提交：客观题 $objectiveCount 题（系统阅卷），主观题 $subjectiveCount 题（满分自批）" +
            (if (scoreText != null) "，本次得分 $scoreText" else "") +
            "，并完成交卷自批"
    }

    private fun JsonObject.extractScoreText(): String? {
        val topKeys = listOf("score", "totalScore", "myScore", "answerScore", "realScore", "scoreText", "finalScore", "selfScore", "scoreDetail")
        for (k in topKeys) {
            val v = this[k]
            val s = when (v) {
                is JsonPrimitive -> v.contentOrNull
                is JsonObject -> v.str("score") ?: v.str("value") ?: v.str("text") ?: v.str("total")
                else -> null
            }
            if (!s.isNullOrBlank() && s != "0" && s != "0.0") {
                return s
            }
        }
        optArr("questionInfoList")?.let { list ->
            val items = list.mapNotNull { it as? JsonObject }
            val gained = items.sumOf { it.doubleOr("score", 0.0) }
            val full = items.sumOf { it.doubleOr("fullScore", 0.0) }
            if (gained > 0) {
                return if (full > 0) "${formatScore(gained)} / ${formatScore(full)}" else formatScore(gained)
            }
        }
        return null
    }

    private fun formatScore(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

    /** 一键刷单卷：打开 → 题目 → 解锁 → 逐题答案 → 提交（带进度回调） */
    suspend fun brushPaper(
        paper: Paper,
        onProgress: (String) -> Unit = {},
    ): String {
        onProgress("初始化：${paper.title}")
        val session = openPaper(paper)
        onProgress("获取题目…")
        val questions = fetchQuestions(session)
        unlockPaper(session)
        val answers = mutableMapOf<String, QuestionAnswer>()
        questions.forEachIndexed { i, q ->
            onProgress("获取答案 ${i + 1}/${questions.size}")
            fetchAnswer(session, q)?.let { answers[q.questionId] = it }
        }
        onProgress("提交并交卷…")
        return submitPaperAnswers(paper, questions, answers)
    }

    // ── 字段提取辅助 ────────────────────────────────────────────

    private fun extractAnswerText(data: JsonObject): String {
        data.optArr("rightAnswer")?.let { arr ->
            extractItems(arr)?.let { return it }
        }
        data.str("rightAnswer")?.let { raw ->
            parseAnswerString(raw)?.let { return it }
        }
        for (key in listOf(
            "rightAnswers", "answers", "answer", "standardAnswer", "correctAnswer",
            "answerContent", "trueAnswer", "myAnswer", "answerList",
        )) {
            when (val v = data[key]) {
                is JsonArray -> extractItems(v)?.let { return it }
                is JsonPrimitive -> v.contentOrNull?.let { parseAnswerString(it) }?.let { return it }
                is JsonObject -> (v.str("content") ?: v.str("answer"))
                    ?.let { HtmlCleaner.clean(it) }
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                else -> {}
            }
        }
        return ""
    }

    private fun extractChoiceList(data: JsonObject): List<String> {
        data.optArr("rightAnswer")?.let { arr ->
            val items = arr.mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull
                    is JsonObject -> el.str("content") ?: el.str("answer")
                    else -> null
                }
            }
            val opts = HtmlCleaner.extractChoiceAnswers(items)
            if (opts.isNotEmpty()) return opts
        }
        data.str("rightAnswer")?.let { raw ->
            runCatching {
                val arr = Json.parseToJsonElement(raw) as? JsonArray ?: return@runCatching null
                arr.mapNotNull { it.str() }
            }.getOrNull()?.let { items ->
                val opts = HtmlCleaner.extractChoiceAnswers(items)
                if (opts.isNotEmpty()) return opts
            }
        }
        return emptyList()
    }

    private fun extractItems(arr: JsonArray): String? {
        val items = arr.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.contentOrNull
                is JsonObject -> el.str("content") ?: el.str("answer") ?: el.str("value") ?: el.str("rightAnswer")
                else -> null
            }
        }
        return HtmlCleaner.formatRightAnswer(items).takeIf { it.isNotBlank() }
    }

    private fun parseAnswerString(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("[") && t.endsWith("]")) {
            runCatching {
                val arr = Json.parseToJsonElement(t) as? JsonArray ?: return@runCatching null
                HtmlCleaner.formatRightAnswer(arr.mapNotNull { it.str() })
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val letters = t.split(',', '，', '、', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (letters.isNotEmpty() && letters.all { Regex("^[A-Za-z]+$").matches(it) }) {
            return letters.joinToString(", ")
        }
        return HtmlCleaner.clean(t).takeIf { it.isNotBlank() }
    }

    private fun extractAnalysisHtml(data: JsonObject): String {
        for (key in listOf(
            "analyse", "analysis", "analysisContent", "analyseContent",
            "answerAnalysis", "parse", "parseContent", "explanation",
        )) {
            when (val v = data[key]) {
                is JsonPrimitive -> v.contentOrNull?.let { if (it.isNotBlank()) return it }
                is JsonObject -> (v.str("content") ?: v.str("text"))
                    ?.let { if (it.isNotBlank()) return it }
                else -> {}
            }
        }
        return ""
    }
}
