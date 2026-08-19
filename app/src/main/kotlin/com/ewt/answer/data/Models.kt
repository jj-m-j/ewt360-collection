package com.ewt.answer.data

/** EWT 用户信息 */
data class UserInfo(
    val userId: String,
    val realName: String,
    val schoolId: String,
)

/** EWT 作业 */
data class HomeworkItem(
    val homeworkId: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
)

/** 独立试卷 / 课后习题（展示用） */
data class Paper(
    val homeworkId: Long,
    val homeworkTitle: String,
    val paperId: String,
    val title: String,
    val questionCount: String,
    val ratio: Double?,
    val date: String,
    val subjectName: String,
    /** 业务编码：205=作业试卷，204=课后习题（来自链接 URL 参数） */
    val bizCode: String = EwtApi.BIZ_SUBMIT,
)

/** 按作业分组的试卷 */
data class HomeworkGroup(
    val homework: HomeworkItem,
    val papers: List<Paper>,
)

/** 试卷会话：获取题目 / 答案所需参数 */
data class PaperSession(
    val paperId: String,
    val platform: String,
    val bizCode: String,
    val reportId: String,
    val title: String,
    /** 试卷总题数（report 接口返回，用于主页展示） */
    val questionCount: Int = 0,
)

/** 题目（统一结构，兼容题组与非题组） */
data class QuestionItem(
    val questionId: String,
    val questionNumber: String,
    val questionType: String,
    val cateId: Int,
    val subjective: Boolean,
    val groupName: String,
    /** 分值（用于非选择题自批） */
    val score: Double = 0.0,
)

/** 单题答案结果 */
data class QuestionAnswer(
    val question: QuestionItem,
    /** 展示用答案文本（已清洗） */
    val answer: String,
    /** 解析原始 HTML（保留公式图），展示时转分段 */
    val analysisHtml: String,
    /** 知识点标题列表 */
    val knowledges: List<String>,
    /** 附件图片 URL */
    val attachmentImages: List<String>,
    /** 选择题答案字母（如 ["A","C"]，用于提交） */
    val choiceAnswers: List<String> = emptyList(),
    /** 接口原始返回（调试用，定位字段结构） */
    val rawJson: String = "",
)

/** 从粘贴的 EWT 试题链接解析出的参数 */
data class PaperLink(
    val paperId: String,
    val platform: String,
    val bizCode: String,
    val homeworkId: String?,
)

/** 解析 EWT 试题链接 */
object PaperLinkParser {
    /**
     * 支持形如：
     * https://web.ewt360.com/answer-pc/exam/answer?paperId=xxx&platform=1&bizCode=201
     * https://web.ewt360.com/answer-pc/exam/answer?bizCode=204&paperId=xxx（课后习题）
     * https://gateway.ewt360.com/...?paperId=xxx
     */
    fun parse(raw: String): PaperLink? {
        val url = raw.trim()
        val qIndex = url.indexOf('?')
        if (qIndex < 0) return null
        val params = mutableMapOf<String, String>()
        url.substring(qIndex + 1).split('&').forEach { part ->
            val kv = part.split('=', limit = 2)
            if (kv.size == 2 && kv[0].isNotBlank()) {
                val decoded = runCatching { java.net.URLDecoder.decode(kv[1], "UTF-8") }.getOrDefault(kv[1])
                params[kv[0]] = decoded
            }
        }
        val paperId = params["paperId"]?.takeIf { it.isNotBlank() } ?: return null
        return PaperLink(
            paperId = paperId,
            platform = params["platform"] ?: EwtApi.PLATFORM,
            bizCode = params["bizCode"] ?: EwtApi.BIZ_SUBMIT,
            homeworkId = params["homeworkId"],
        )
    }

    fun toPaper(link: PaperLink, title: String = "链接导入"): Paper = Paper(
        homeworkId = link.homeworkId?.toLongOrNull() ?: 0L,
        homeworkTitle = "通过链接导入",
        paperId = link.paperId,
        title = title,
        questionCount = "?",
        ratio = null,
        date = "",
        subjectName = "",
        bizCode = link.bizCode,
    )
}
