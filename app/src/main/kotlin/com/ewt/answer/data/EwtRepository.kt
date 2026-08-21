    private suspend fun initReportId(paper: Paper): Triple<String, String, Int> {
        val extId = paper.homeworkId
        val biz = paper.bizCode
        // 取答案/查看一律用 homeworkId='0' 的专用锁卷报告（opt.js 同款）：
        // 锁卷（空交）后 finish=true，analysis 才返回正确答案；homeworkId=0 的报告不归属作业，空交不污染任务成绩。
        // 正式作业报告只用于提交（见 submitPaperAnswers / initSubmitReportId）。
        val attempts = listOf(
            Triple("lock+ext0+rep0", biz, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, biz, 0, 0) }),
            Triple("lock+ext0+rep1", biz, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, biz, 0, 1) }),
            Triple("$biz+ext$extId+rep0", biz, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, biz, extId, 0) }),
            Triple("$biz+ext$extId+rep1", biz, suspend { EwtEndpoints.initReport(paper.paperId, EwtApi.PLATFORM, biz, extId, 1) }),
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
        throw EwtException("初始化答卷失败：无 reportId（已尝试 6 种方式，详见日志）")
    }
