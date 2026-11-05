package com.resumeagent.app

import com.resumeagent.app.data.ProjectJson
import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.domain.TraceabilityEntry
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
            draftSections = listOf(
                DraftSection(
                    title = "工作经历",
                    content = "参与需求分析。",
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
            )
        )

        val decoded = ProjectJson.decode(ProjectJson.encode(project))

        assertEquals("产品经理", decoded.targetRole)
        assertEquals("核实团队规模", decoded.verificationChecklist.single())
        assertEquals("有原文依据", decoded.traceability.single().status)
        assertTrue(decoded.draftSections.single().sourceEvidence.isNotBlank())
    }
}
