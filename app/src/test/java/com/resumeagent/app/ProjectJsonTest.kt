package com.resumeagent.app

import com.resumeagent.app.data.ProjectJson
import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.FactClaim
import com.resumeagent.app.domain.OptimizationMode
import com.resumeagent.app.domain.ParagraphRevision
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.domain.RuleCheck
import com.resumeagent.app.domain.SeniorityLevel
import com.resumeagent.app.domain.TraceabilityEntry
import com.resumeagent.app.domain.ProjectVersion
import com.resumeagent.app.domain.ResumeAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectJsonTest {
    @Test
    fun roundTripsFactAuditData() {
        val project = ResumeProject(
            title = "测试任务",
            targetRole = "产品经理",
            resumeText = "参与需求分析。",
            jobDescription = "负责需求分析。",
            optimizationMode = OptimizationMode.TARGETED,
            seniorityLevel = SeniorityLevel.MID,
            analysis = ResumeAnalysis(
                overallScore = 80,
                fitSummary = "经历相关",
                dimensions = emptyList(),
                strengths = emptyList(),
                issues = emptyList(),
                missingQuestions = emptyList(),
                jobKeywords = emptyList(),
                shouldReorderSections = true,
                recommendedSectionOrder = listOf("项目经历", "工作经历"),
                sectionOrderAdvice = "岗位项目经验被放得过后。"
            ),
            factLedger = listOf(FactClaim(quote = "参与需求分析。")),
            ruleChecks = listOf(RuleCheck("联系方式", false, "缺少联系方式")),
            draftSections = listOf(
                DraftSection(
                    title = "工作经历",
                    content = "参与需求分析。",
                    benefit = "职责边界保持真实",
                    originalContent = "参与需求分析。",
                    sourceEvidence = "参与需求分析。"
                )
            ),
            verificationChecklist = listOf("核实团队规模"),
            traceability = listOf(
                TraceabilityEntry(
                    fact = "参与需求分析",
                    rewrittenText = "参与需求分析。",
                    sourceEvidence = "参与需求分析。",
                    status = "有原文依据"
                )
            ),
            versions = listOf(
                ProjectVersion(
                    label = "V1",
                    instruction = "客户要求压缩项目经历",
                    draftSections = listOf(
                        DraftSection(title = "项目经历", content = "参与需求分析。")
                    )
                )
            ),
            activeVersionId = "active",
            finalVersionId = "active",
            layoutRevision = listOf(
                ParagraphRevision(
                    index = 1,
                    original = "参与需求分析。",
                    revised = "参与需求分析，整理业务规则。",
                    reason = "补充原文已有工作内容"
                )
            )
        )

        val decoded = ProjectJson.decode(ProjectJson.encode(project))

        assertEquals("产品经理", decoded.targetRole)
        assertEquals("核实团队规模", decoded.verificationChecklist.single())
        assertEquals("有原文依据", decoded.traceability.single().status)
        assertEquals(OptimizationMode.TARGETED, decoded.optimizationMode)
        assertTrue(decoded.analysis?.shouldReorderSections == true)
        assertEquals(listOf("项目经历", "工作经历"), decoded.analysis?.recommendedSectionOrder)
        assertEquals(SeniorityLevel.MID, decoded.seniorityLevel)
        assertEquals("参与需求分析。", decoded.factLedger.single().quote)
        assertEquals("缺少联系方式", decoded.ruleChecks.single().detail)
        assertEquals("V1", decoded.versions.single().label)
        assertEquals("active", decoded.activeVersionId)
        assertEquals("active", decoded.finalVersionId)
        assertEquals(1, decoded.layoutRevision.single().index)
        assertEquals("参与需求分析，整理业务规则。", decoded.layoutRevision.single().revised)
        assertEquals("职责边界保持真实", decoded.draftSections.single().benefit)
        assertEquals("参与需求分析。", decoded.draftSections.single().originalContent)
        assertTrue(decoded.draftSections.single().sourceEvidence.isNotBlank())
    }
}
