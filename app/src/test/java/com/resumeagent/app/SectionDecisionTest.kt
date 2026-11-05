package com.resumeagent.app

import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.ResumeProject
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionDecisionTest {
    @Test
    fun exportsRewriteOrOriginalAndSkipsExcludedSection() {
        val project = ResumeProject(
            title = "测试",
            resumeText = "原文",
            jobDescription = "",
            draftSections = listOf(
                DraftSection(
                    title = "改写",
                    content = "改写后",
                    originalContent = "改写前",
                    sourceEvidence = "改写前"
                ),
                DraftSection(
                    title = "保留",
                    content = "不应使用",
                    originalContent = "应保留的原文",
                    sourceEvidence = "应保留的原文",
                    keepOriginal = true
                ),
                DraftSection(
                    title = "不导出",
                    content = "不应出现",
                    originalContent = "原文",
                    sourceEvidence = "原文",
                    accepted = false
                )
            )
        )

        assertEquals(listOf("改写后", "应保留的原文"), project.acceptedSections.map { it.content })
    }
}
