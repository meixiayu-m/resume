package com.resumeagent.app

import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.FactGuard
import com.resumeagent.app.domain.ResumeAnalysis
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.domain.ScoreDimension
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FactGuardTest {
    @Test
    fun rejectsUnsupportedNumbersOwnershipAndKeywords() {
        val project = ResumeProject(
            title = "测试任务",
            targetRole = "后端工程师",
            resumeText = "参与支付系统开发，服务10万用户。",
            jobDescription = "需要 Kubernetes 经验。",
            analysis = ResumeAnalysis(
                overallScore = 50,
                fitSummary = "需要核实",
                dimensions = listOf(ScoreDimension("岗位匹配", 50, "一般")),
                strengths = emptyList(),
                issues = emptyList(),
                missingQuestions = emptyList(),
                jobKeywords = listOf("Kubernetes")
            )
        )
        val section = DraftSection(
            title = "工作经历",
            content = "负责支付系统开发，服务20万用户，使用 Kubernetes 完成迁移。",
            sourceEvidence = "参与支付系统开发，服务10万用户。"
        )

        val result = FactGuard.audit(project, listOf(section), emptyList(), emptyList())

        assertFalse(result.sections.single().accepted)
        assertTrue(result.sections.single().verificationStatus.contains("新增数字 20"))
        assertTrue(result.verificationChecklist.any { it.contains("负责") })
        assertTrue(result.verificationChecklist.any { it.contains("Kubernetes") })
        assertTrue(result.traceability.isNotEmpty())
    }

    @Test
    fun removesEmojiAndExclamationFromOutput() {
        val sanitized = FactGuard.sanitize("负责项目\u26A0！结果良好！")

        assertFalse(sanitized.contains("！"))
        assertFalse(sanitized.contains("!"))
        assertFalse(sanitized.contains("\u26A0"))
    }

    @Test
    fun rejectsEvidenceThatDoesNotExistInOriginalMaterial() {
        val project = ResumeProject(
            title = "测试任务",
            targetRole = "运营",
            resumeText = "参与活动运营。",
            jobDescription = "负责用户增长。"
        )
        val section = DraftSection(
            title = "工作经历",
            content = "参与活动运营，覆盖多个渠道。",
            sourceEvidence = "独立负责全渠道用户增长。"
        )

        val result = FactGuard.audit(project, listOf(section), emptyList(), emptyList())

        assertFalse(result.sections.single().accepted)
        assertTrue(result.sections.single().verificationStatus.contains("原文依据"))
    }
}
