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
 * 支持作业试卷（205）与课后习题（204，经 opt.js 的 queryStudentLessonStudyGuideAndPractice 发现）。
 */
class EwtRepository(private val tokenStore: SecureTokenStore) {

    /** 恢复持久化 token 到内存，返回是否成功 */
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

    // ── 用户 / 登录态 ───────────────────────────────────────────

    /** 校验登录态并获取用户信息（失败即 token 失效） */
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

    /** 获取全部作业（status 1/2/3 合并去重，按结束时间倒序） */
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

    /**
     * 扫描单个作业下所有可答题任务（试卷 205 + 课后习题 204）。
     * 逻辑移植自 opt.js：
     *   getStudentHomeworkDaySubjectStat（日期统计，失败回退按 12 学科）
     *   → student/homework/task/pageHomeworkTasks（任务列表，试卷 = contentType==2 / 含"试卷" / code==205）
     *   → 非试卷任务收集 lessonId+taskId → queryStudentLessonStudyGuideAndPractice（课后习题 paperId + bizCode）
     */
    suspend fun scanHomeworkPapers(schoolId: String, homework: HomeworkItem): List<Paper> {
        val papers = mutableListOf<Paper>()
        val hid = homework.homeworkId

        // 1. 日期/学科统计
        var dayList = mutableListOf<Pair<String?, Int?>>()
        try {
            val statData = EwtEndpoints.getStudentHomeworkDaySubjectStat(schoolId, hid).optObj("data")
            val days = statData?.optArr("dateStat") ?: statData?.optArr("dayStat")
                ?: statData?.optArr("days") ?: statData?.optArr("list")
            for (el in days ?: emptyList()) {
                val o = el as? JsonObject ?: continue
                val dayId = o.str("dayId")
                if (dayId != null) {
                    dayList.add(dayId to null)
                } else {
                    val sid = o.intOr("subjectId", 0)
                    if (sid > 0) dayList.add(null to sid)
                }
            }
        } catch (e: Exception) {
            DebugLog.e("Scan", "日期统计失败，回退按学科", e)
        }
        if (dayList.isEmpty()) {
            dayList = (1..12).map { null to it }.toMutableList()
        }

        // 2. 逐天/学科拉任务 + 查课后习题
        for ((dayId, subjectId) in dayList) {
            try {
                val tasks = EwtEndpoints.pageHomeworkTasksOpt(schoolId, hid, dayId, subjectId) ?: continue
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
                        DebugLog.d("Scan", "收录试卷: ${task.str("title")} paperId=$pid")
                        papers.add(
                            Paper(
                                homeworkId = hid,
                                homeworkTitle = homework.title,
                                paperId = pid,
                                title = task.str("title") ?: "未知试卷",
                                questionCount = task.str("questionCount") ?: task.str("itemCount") ?: "?",
                                ratio = task.doubleOr("ratio", 0.0).takeIf { it > 0 },
                                date = "",
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

                // 3. 课后习题查询
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
                            DebugLog.d("Scan", "收录课后习题: ${task?.str("title")} paperId=$pid biz=$biz")
                            papers.add(
                                Paper(
                                    homeworkId = hid,
                                    homeworkTitle = homework.title,
                                    paperId = pid,
                                    title = task?.str("title") ?: "课程练习",
                                    questionCount = studyTest.str("questionCount") ?: "?",
                                    ratio = null,
                                    date = "",
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
                DebugLog.e("Scan", "任务拉取失败 day=$dayId subj=$subjectId", e)
            }
        }
        return papers
    }

    /** 扫描全部作业（带进度回调） */
    suspend fun scanAllPapers(onProgress: (String) -> Unit = {}): List<HomeworkGroup> {
        val user = fetchUserInfo()
        if (user.schoolId.isBlank()) throw EwtException("未获取到学校信息")
        onProgress("正在获取作业列表…")
        val homeworks = fetchHomeworks(user.schoolId)
        if (homeworks.isEmpty()) return emptyList()
        DebugLog.d("Scan", "作业数=${homeworks.size}")
        val groups = mutableListOf<HomeworkGroup>()
        homeworks.forEachIndexed { i, hw ->
            onProgress("扫描作业 ${i + 1}/${homeworks.size}：${hw.title}")
            val papers = scanHomeworkPapers(user.schoolId, hw)
            DebugLog.d("Scan", "作业 ${hw.homeworkId} 收录 ${papers.size} 个任务")
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

    /** 打开试卷：按试卷 bizCode 初始化 report（205 作业 / 204 课后习题），返回会话。 */
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

    /** 初始化 report：优先按试卷 bizCode（204/205），失败回退 205 / 201。 */
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
                .onFailure { e ->
                    DebugLog.e("Init", "候选[$label] 失败 paperId=${paper.paperId}", e)
                }
                .getOrNull()
            if (result != null) {
                val id = result.str("reportId") ?: result.str("report") ?: result.str("id")
                if (id != null) {
                    val count = result.intOr("questionCount", 0)
                    DebugLog.d("Init", "候选[$label] 成功 reportId=$id count=$count paperId=${paper.paperId}")
                    return Triple(id, b, count)
                }
            }
        }
        throw EwtException("初始化答卷失败：无 reportId（已尝试 5 种方式，详见日志）")
    }

    /** 提交专用 report 初始化：按试卷 bizCode（204/205），isRepeat 0→1 */
    private suspend fun initSubmitReportId(paper: Paper, biz: String): String {
        val extId = paper.homeworkId
        for (isRepeat in listOf(0, 1)) {
            val id = runCatching {
                EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, biz, extId, isRepeat)
                    .optObj("data")?.let { it.str("reportId") ?: it.str("report") ?: it.str("id") }
            }.onFailure { e ->
                DebugLog.e("Submit", "提交 report 初始化失败 biz=$biz isRepeat=$isRepeat paperId=${paper.paperId}", e)
            }.getOrNull()
            if (id != null) {
                DebugLog.d("Submit", "提交 report 初始化成功 reportId=$id biz=$biz")
                return id
            }
        }
        throw EwtException("初始化提交答卷失败：无 reportId")
    }

    /** 空交卷解锁：仅上报作答时长，不提交任何答案内容 */
    suspend fun unlockPaper(session: PaperSession) {
        DebugLog.d("Unlock", "空交卷解锁 paperId=${session.paperId} reportId=${session.reportId} biz=${session.bizCode}")
        EwtEndpoints.updateReport(session.paperId, session.reportId, session.platform, session.bizCode)
    }

    /** 获取题目列表：优先题组接口，失败回退非题组接口 */
    suspend fun fetchQuestions(session: PaperSession): List<QuestionItem> {
        return try {
            fetchGroupedQuestions(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.d("Ques", "题组接口失败，回退非题组: ${e.message}")
            fetchFlatQuestions(session)
        }
    }

    private suspend fun fetchGroupedQuestions(session: PaperSession): List<QuestionItem> {
        val data = EwtEndpoints.getAnswerSheetSubGroup(
            session.paperId, session.reportId, session.platform, session.bizCode,
        ).optObj("data") ?: throw EwtException("题组数据为空")
        val list = data.optArr("groupQuestionList")
            ?: throw EwtException("题组数据为空")
        val questions = mutableListOf<QuestionItem>()
        for (g in list) {
            val group = g as? JsonObject ?: continue
            val groupName = group.str("groupName").orEmpty()
            for (q in group.optArr("questionList") ?: emptyList()) {
                val qo = q as? JsonObject ?: continue
                val qid = qo.str("questionId") ?: continue
                questions.add(
                    QuestionItem(
                        questionId = qid,
                        questionNumber = qo.strOr("questionNumber", ""),
                        questionType = qo.str("questionType").orEmpty(),
                        cateId = qo.intOr("cateId", 1),
                        subjective = qo.boolOr("subjective", false),
                        groupName = groupName,
                        score = qo.doubleOr("score", 0.0),
                    ),
                )
            }
        }
        DebugLog.d("Ques", "题组题目 ${questions.size} 道 paperId=${session.paperId} reportId=${session.reportId} biz=${session.bizCode}")
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
        for (el in list) {
            val qo = el as? JsonObject ?: continue
            val qid = qo.str("questionId") ?: continue
            questions.add(
                QuestionItem(
                    questionId = qid,
                    questionNumber = qo.strOr("questionNumber", ""),
                    questionType = qo.str("questionType").orEmpty(),
                    cateId = qo.intOr("cateId", 1),
                    subjective = qo.boolOr("subjective", false),
                    groupName = "",
                    score = qo.doubleOr("score", 0.0),
                ),
            )
        }
        DebugLog.d("Ques", "非题组题目 ${questions.size} 道 paperId=${session.paperId}")
        return questions
    }

    // ── 答案获取（ewt-getanwser.js + opt.js 混合题型） ───────────

    /** 获取单题答案。混合题型（复合题）父题答案为空时拼接 childQuestions 子题答案/解析。 */
    suspend fun fetchAnswer(session: PaperSession, question: QuestionItem): QuestionAnswer? {
        return try {
            val data = EwtEndpoints.getQuestionAnalysis(
                session.paperId, session.reportId, session.platform, question.questionId, session.bizCode,
            ).optObj("data") ?: return null

            var answerStr = extractAnswerText(data)
            var analysisHtml = extractAnalysisHtml(data)
            val choiceAnswers = extractChoiceList(data).toMutableList()

            val childQs = data.optArr("childQuestions") ?: emptyList()
            if (childQs.isNotEmpty()) {
                // 复合题：父题 rightAnswer=[]/analyse=""，答案在子题中
                if (answerStr.isBlank()) {
                    val sb = StringBuilder()
                    childQs.forEachIndexed { idx, c ->
                        val co = c as? JsonObject ?: return@forEachIndexed
                        val childRight = co.optArr("rightAnswer")?.mapNotNull { it.str() } ?: emptyList()
                        val opts = HtmlCleaner.extractChoiceAnswers(childRight)
                        if (opts.isNotEmpty()) choiceAnswers.addAll(opts)
                        val ans = when {
                            opts.isNotEmpty() -> opts.joinToString(", ")
                            childRight.isNotEmpty() -> childRight.map { HtmlCleaner.clean(it) }.joinToString("; ")
                            else -> "(主观题)"
                        }
                        sb.append("(${idx + 1}) ").append(ans).append("\n")
                    }
                    answerStr = sb.toString().trim()
                }
                if (analysisHtml.isBlank()) {
                    val sb = StringBuilder()
                    childQs.forEachIndexed { idx, c ->
                        val co = c as? JsonObject ?: return@forEachIndexed
                        val analyse = co.str("analyse").orEmpty()
                        if (analyse.isNotBlank()) sb.append("\n(${idx + 1}) ").append(analyse)
                    }
                    analysisHtml = sb.toString().trim()
                }
            }

            val knowledges = data.optArr("knowledges")
                ?.mapNotNull { (it as? JsonObject)?.str("title") }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val images = data.optArr("attachmentImages")
                ?.mapNotNull { it.str() }
                ?.filter { it.startsWith("http") }
                ?: emptyList()

            if (answerStr.isBlank() && analysisHtml.isBlank()) {
                DebugLog.e("Ans", "答案/解析为空 q=${question.questionId} raw=${data.toString().take(600)}")
            }

            QuestionAnswer(
                question = question,
                answer = answerStr,
                analysisHtml = analysisHtml,
                knowledges = knowledges,
                attachmentImages = images,
                choiceAnswers = choiceAnswers,
                rawJson = data.toString(),
            )
        } catch (e: Exception) {
            DebugLog.e("Ans", "获取答案异常 q=${question.questionId}", e)
            null
        }
    }

    // ── 提交（用户确认后调用；EWT-TOOL-main paperFiller 流程） ──

    /** 提交整卷答案并交卷自批（按试卷 bizCode：205 作业 / 204 课后习题）。返回结果描述。 */
    suspend fun submitPaperAnswers(
        paper: Paper,
        questions: List<QuestionItem>,
        answers: Map<String, QuestionAnswer>,
    ): String {
        val biz = paper.bizCode
        val reportId = initSubmitReportId(paper, biz)

        val sel = mutableListOf<JsonObject>()
        val notSel = mutableListOf<JsonObject>()
        for (q in questions) {
            val a = answers[q.questionId] ?: continue
            val opts = a.choiceAnswers
            if (opts.isNotEmpty()) {
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
        DebugLog.d("Submit", "biz=$biz 选择题 ${sel.size} 题，非选择题 ${notSel.size} 题 reportId=$reportId")
        if (sel.isNotEmpty()) {
            EwtEndpoints.submitAnswer(paper.paperId, reportId, EwtApi.PLATFORM, biz, JsonArray(sel))
        }
        if (notSel.isNotEmpty()) {
            EwtEndpoints.submitAnswer(paper.paperId, reportId, EwtApi.PLATFORM, biz, JsonArray(notSel))
        }
        EwtEndpoints.submitPaper(paper.paperId, reportId, EwtApi.PLATFORM, biz)
        EwtEndpoints.submitCorrected(paper.paperId, reportId, EwtApi.PLATFORM, biz)
        return "已提交：选择题 ${sel.size} 题，非选择题 ${notSel.size} 题，并完成交卷自批"
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
