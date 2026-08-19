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
 * 仓库层：组合 EWT-TOOL-main（扫描试卷/刷卷）与 ewt-getanwser.js（获取答案）能力。
 *
 * 数据流：
 * 扫描任务（作业内所有带 paperId 的任务：试卷 / 课后习题）
 *   → initReport 多候选探测（205 带参 → 205 isRepeat=1 → 201 查看态）
 *   → 题目列表（题组 getAnswerSheetSubGroup / 非题组 answerSheetInfo）
 *   → 空交卷解锁（updateReport，答案接口在交卷后才返回答案/解析）
 *   → 逐题答案（simple/question/analysis）
 *   → 提交（用户确认后）：submitAnswer（选择题标准答案 + 非选择题自批）+ submitPaper + submitCorrected
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

    // ── 作业 / 任务扫描（EWT-TOOL-main） ────────────────────────

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
     * 扫描单个作业下所有带 paperId 的任务（试卷 / 课后习题等）。
     * 不做 contentTypeName 过滤：只要 contentUrl 中含 paperId 就收录。
     */
    suspend fun scanHomeworkPapers(schoolId: String, homework: HomeworkItem): List<Paper> {
        val papers = mutableListOf<Paper>()
        try {
            val dist = EwtEndpoints.studentHomeworkDistribution(listOf(homework.homeworkId), schoolId)
                .optObj("data")
            val days = dist?.optArr("days") ?: return papers
            for (dayEl in days) {
                val dayObj = dayEl as? JsonObject ?: continue
                val dayId = dayObj.optArr("dayId")?.firstOrNull()?.str() ?: continue
                val dayTs = dayObj.longOr("day", 0L)
                val dateStr = formatDate(dayTs)
                try {
                    val tasks = EwtEndpoints.pageHomeworkTasks(homework.homeworkId, dayId, dayTs, schoolId)
                        ?: continue
                    for (t in tasks) {
                        val task = t as? JsonObject ?: continue
                        val typeName = task.str("contentTypeName").orEmpty()
                        val paperId = extractPaperId(task)
                        if (paperId == null) {
                            DebugLog.d("Scan", "无 paperId 跳过: type=$typeName title=${task.str("title")}")
                            continue
                        }
                        DebugLog.d("Scan", "收录任务: type=$typeName title=${task.str("title")} paperId=$paperId")
                        papers.add(
                            Paper(
                                homeworkId = homework.homeworkId,
                                homeworkTitle = homework.title,
                                paperId = paperId,
                                title = task.str("title") ?: "未知任务",
                                questionCount = task.str("questionCount") ?: "?",
                                ratio = task.doubleOr("ratio", 0.0).takeIf { it > 0 },
                                date = dateStr,
                                subjectName = task.str("subjectName").orEmpty(),
                            ),
                        )
                    }
                } catch (e: Exception) {
                    DebugLog.e("Scan", "任务拉取失败 day=$dayId", e)
                }
            }
        } catch (e: Exception) {
            DebugLog.e("Scan", "分布接口失败 hw=${homework.homeworkId}", e)
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

    private fun extractPaperId(task: JsonObject): String? {
        val url = task.str("contentUrl").orEmpty()
        Regex("paperId=([^&]+)").find(url)?.let { return it.groupValues[1] }
        return task.str("contentId")
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

    /**
     * 打开试卷：多候选初始化 report，返回 (reportId, bizCode)。
     * 候选顺序：
     *   1) 205 + extId + isRepeat=0（EWT-TOOL-main，未做试卷可初始化）
     *   2) 205 + extId + isRepeat=1（已做过试卷重试）
     *   3) 201 查看态（JS，已做过试卷可用）
     */
    suspend fun openPaper(paper: Paper): PaperSession {
        val (reportId, bizCode) = initReportId(paper)
        return PaperSession(
            paperId = paper.paperId,
            platform = EwtApi.PLATFORM,
            bizCode = bizCode,
            reportId = reportId,
            title = paper.title,
        )
    }

    private suspend fun initReportId(paper: Paper): Pair<String, String> {
        val extId = paper.homeworkId
        val attempts = listOf(
            Triple("205+ext$extId+rep0", EwtApi.BIZ_SUBMIT, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, EwtApi.BIZ_SUBMIT, extId, 0) }),
            Triple("205+ext$extId+rep1", EwtApi.BIZ_SUBMIT, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, EwtApi.BIZ_SUBMIT, extId, 1) }),
            Triple("205+noext", EwtApi.BIZ_SUBMIT, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, EwtApi.BIZ_SUBMIT, 0, 0) }),
            Triple("201+view", EwtApi.BIZ_VIEW, suspend { EwtEndpoints.getReportIdView(paper.paperId, EwtApi.PLATFORM, EwtApi.BIZ_VIEW) }),
        )
        for ((label, biz, call) in attempts) {
            val id = runCatching {
                val data = call().optObj("data")
                data?.str("reportId") ?: data?.str("report") ?: data?.str("id")
            }.onFailure { e ->
                DebugLog.e("Init", "候选[$label] 失败 paperId=${paper.paperId}", e)
            }.getOrNull()
            if (id != null) {
                DebugLog.d("Init", "候选[$label] 成功 reportId=$id paperId=${paper.paperId}")
                return id to biz
            }
        }
        throw EwtException("初始化答卷失败：无 reportId（已尝试 4 种方式，详见日志）")
    }

    /** 空交卷解锁：仅上报作答时长，不提交任何答案内容（答案/解析接口在交卷后才返回） */
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
        DebugLog.d("Ques", "题组题目 ${questions.size} 道 paperId=${session.paperId}")
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

    // ── 答案获取（ewt-getanwser.js） ────────────────────────────

    /**
     * 获取单题答案。返回 null 表示该题获取失败（不抛异常，避免单题拖垮整体）。
     */
    suspend fun fetchAnswer(session: PaperSession, question: QuestionItem): QuestionAnswer? {
        return try {
            val data = EwtEndpoints.getQuestionAnalysis(
                session.paperId, session.reportId, session.platform, question.questionId, session.bizCode,
            ).optObj("data") ?: return null

            val answerStr = extractAnswerText(data)
            val choiceAnswers = extractChoiceList(data)
            val knowledges = data.optArr("knowledges")
                ?.mapNotNull { (it as? JsonObject)?.str("title") }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val images = data.optArr("attachmentImages")
                ?.mapNotNull { it.str() }
                ?.filter { it.startsWith("http") }
                ?: emptyList()
            val analysisHtml = extractAnalysisHtml(data)

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

    /**
     * 提交整卷答案并交卷自批：
     * - 选择题：提交标准答案字母
     * - 非选择题：提交占位答案并标记 revision 自批（EWT-TOOL-main 逻辑）
     * - submitpaper 交卷 → submitCorrected 自批
     * 返回结果描述。
     */
    suspend fun submitPaperAnswers(
        paper: Paper,
        questions: List<QuestionItem>,
        answers: Map<String, QuestionAnswer>,
    ): String {
        val biz = EwtApi.BIZ_SUBMIT
        // 初始化提交用 report（bizCode=205）
        val reportId = initReportId(paper).first

        val sel = mutableListOf<JsonObject>()
        val notSel = mutableListOf<JsonObject>()
        for (q in questions) {
            val a = answers[q.questionId] ?: continue
            val opts = a.choiceAnswers
            if (opts.isNotEmpty()) {
                // 选择题：提交答案字母
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
                // 非选择题：占位答案 + revision 自批
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
        DebugLog.d("Submit", "选择题 ${sel.size} 题，非选择题 ${notSel.size} 题 reportId=$reportId")
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

    /**
     * 兼容多种答案字段结构提取答案文本（按优先级尝试）：
     * 1) rightAnswer 数组（元素为字符串 / {content|answer} 对象）
     * 2) rightAnswer 字符串（"A,C" / "AB" / JSON 数组字符串 / 纯文本，如语法填空）
     * 3) 其他候选字段 answers / standardAnswer / correctAnswer / answerContent / trueAnswer / answerList …
     */
    private fun extractAnswerText(data: JsonObject): String {
        // 1. rightAnswer 数组
        data.optArr("rightAnswer")?.let { arr ->
            extractItems(arr)?.let { return it }
        }
        // 2. rightAnswer 字符串
        data.str("rightAnswer")?.let { raw ->
            parseAnswerString(raw)?.let { return it }
        }
        // 3. 候选字段兜底
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

    /** 提取选择题答案字母列表（用于提交） */
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

    /** 从数组中提取答案文本（元素为字符串或对象） */
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

    /** 解析字符串形式的答案：JSON 数组字符串 / "A,C" / "AC" / 纯文本 */
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

    /** 提取解析 HTML（analyse / analysis 等候选字段，兼容字符串与 {content} 对象） */
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
