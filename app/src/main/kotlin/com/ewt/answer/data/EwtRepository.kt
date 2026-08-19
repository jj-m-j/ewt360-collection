package com.ewt.answer.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * 仓库层：组合 EWT-TOOL-main（扫描试卷/题目）与 ewt-getanwser.js（获取答案）能力。
 *
 * 数据流：
 * 扫描试卷（EWT-TOOL-main paperScanner.ts）
 *   → getReportId（ewt-getanwser.js）
 *   → 题目列表（题组 getAnswerSheetSubGroup / 非题组 answerSheetInfo）
 *   → 逐题答案（simple/question/analysis）
 *
 * 注意：本仓库只做「获取 / 解析」，绝不调用 submitAnswer / submitpaper 等写接口。
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

    // ── 作业 / 试卷扫描（EWT-TOOL-main） ────────────────────────

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
                // 单个状态失败不影响其他
            }
        }
        all.sortByDescending { if (it.endTime != 0L) it.endTime else it.startTime }
        return all
    }

    /** 扫描单个作业下的独立试卷（contentTypeName 含“试卷”） */
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
                        if (!typeName.contains("试卷")) continue
                        val paperId = extractPaperId(task) ?: continue
                        papers.add(
                            Paper(
                                homeworkId = homework.homeworkId,
                                homeworkTitle = homework.title,
                                paperId = paperId,
                                title = task.str("title") ?: "未知试卷",
                                questionCount = task.str("questionCount") ?: "?",
                                ratio = task.doubleOr("ratio", 0.0).takeIf { it > 0 },
                                date = dateStr,
                                subjectName = task.str("subjectName").orEmpty(),
                            ),
                        )
                    }
                } catch (e: Exception) {
                    // 单天失败不影响其他天
                }
            }
        } catch (e: Exception) {
            // 分布接口失败返回空
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

    /** 打开试卷：获取 reportId（视图态 bizCode=201，不提交任何内容） */
    suspend fun openPaper(paper: Paper): PaperSession {
        val report = EwtEndpoints.getReportId(paper.paperId, EwtApi.PLATFORM, EwtApi.BIZ_VIEW)
            .optObj("data")
            ?: throw EwtException("初始化答卷失败：无 reportId")
        val reportId = report.str("reportId")
            ?: report.str("report")
            ?: report.str("id")
            ?: throw EwtException("初始化答卷失败：无 reportId")
        return PaperSession(
            paperId = paper.paperId,
            platform = EwtApi.PLATFORM,
            bizCode = EwtApi.BIZ_VIEW,
            reportId = reportId,
            title = paper.title,
        )
    }

    /** 获取题目列表：优先题组接口，失败回退非题组接口 */
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
                ),
            )
        }
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

            val rightAnswer = data.optArr("rightAnswer")?.mapNotNull { it.str() } ?: emptyList()
            val answerStr = HtmlCleaner.formatRightAnswer(rightAnswer)
            val knowledges = data.optArr("knowledges")
                ?.mapNotNull { (it as? JsonObject)?.str("title") }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val images = data.optArr("attachmentImages")
                ?.mapNotNull { it.str() }
                ?.filter { it.startsWith("http") }
                ?: emptyList()
            val analysisHtml = data.str("analyse").orEmpty()

            QuestionAnswer(
                question = question,
                answer = answerStr,
                analysisHtml = analysisHtml,
                knowledges = knowledges,
                attachmentImages = images,
            )
        } catch (e: Exception) {
            null
        }
    }
}
