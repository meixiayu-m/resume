package com.resumeagent.app

import com.resumeagent.app.domain.ClientAddressStyle
import com.resumeagent.app.domain.ClientMessageFormatter
import com.resumeagent.app.domain.ResumeAnalysis
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.domain.SeniorityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientMessageFormatterTest {
    @Test
    fun shortMessageUsesStudentToneAndHasNoLeadingIndent() {
        val message = ClientMessageFormatter.build(
            project = project(SeniorityLevel.ENTRY),
            analysis = analysis(),
            questionLimit = ClientMessageFormatter.SHORT_QUESTION_LIMIT
        )

        assertTrue(message.startsWith("李同学，"))
        assertTrue(message.contains("\n\n1. "))
        assertTrue(message.contains("\n3. "))
        assertFalse(message.contains("\n4. "))
        assertFalse(message.lines().any { it.startsWith(" ") })
        assertTrue(message.contains("你"))
        assertFalse(message.contains("您"))
    }

    @Test
    fun formalToneUsesPoliteAddressAndPronoun() {
        val message = ClientMessageFormatter.build(
            project = project(SeniorityLevel.MANAGER),
            analysis = analysis()
        )

        assertTrue(message.startsWith("李明，您好。"))
        assertTrue(message.contains("您"))
        assertFalse(message.contains("你负责"))
    }

    @Test
    fun manualStudentStyleOverridesSeniority() {
        val message = ClientMessageFormatter.build(
            project = project(
                SeniorityLevel.MANAGER,
                ClientAddressStyle.STUDENT
            ),
            analysis = analysis()
        )

        assertTrue(message.startsWith("李明同学，"))
        assertFalse(message.contains("您好"))
    }

    @Test
    fun fullMessageIncludesAllCappedQuestions() {
        val message = ClientMessageFormatter.build(
            project = project(SeniorityLevel.ENTRY),
            analysis = analysis(),
            questionLimit = ClientMessageFormatter.FULL_QUESTION_LIMIT
        )

        assertEquals(4, Regex("(?m)^\\d+\\. ").findAll(message).count())
    }

    @Test
    fun legacyMessageRemovesCopiedIndentation() {
        val message = ClientMessageFormatter.build(
            project = project(SeniorityLevel.ENTRY),
            analysis = analysis().copy(
                missingQuestions = emptyList(),
                clientQuestionMessage = "\n        第一行\n          第二行\n"
            )
        )

        assertEquals("第一行\n第二行", message)
    }

    private fun project(
        seniority: SeniorityLevel,
        style: ClientAddressStyle = ClientAddressStyle.AUTO
    ) = ResumeProject(
        title = "测试",
        clientName = "李明",
        seniorityLevel = seniority,
        clientAddressStyle = style,
        resumeText = "项目经历"
    )

    private fun analysis() = ResumeAnalysis(
        overallScore = 70,
        fitSummary = "需要核对",
        dimensions = emptyList(),
        strengths = emptyList(),
        issues = emptyList(),
        missingQuestions = listOf(
            "项目里你最常负责哪一部分",
            "这个项目大概由多少人协作",
            "项目最终有没有比较明确的结果",
            "相关工具用了多久"
        ),
        jobKeywords = emptyList(),
        clientQuestionIntro = "简历里的项目信息还需要你补充一下",
        clientQuestionClosing = "按编号回复即可"
    )
}
