package com.resumeagent.app.domain

object ClientMessageFormatter {
    const val SHORT_QUESTION_LIMIT = 3
    const val FULL_QUESTION_LIMIT = 6

    fun build(
        project: ResumeProject,
        analysis: ResumeAnalysis,
        questionLimit: Int = SHORT_QUESTION_LIMIT
    ): String {
        if (analysis.missingQuestions.isEmpty()) {
            return normalizeBlock(analysis.clientQuestionMessage)
        }
        val questions = analysis.missingQuestions
            .map(::normalizeQuestion)
            .filter { it.isNotBlank() }
            .take(questionLimit.coerceIn(1, FULL_QUESTION_LIMIT))
        if (questions.isEmpty()) return normalizeBlock(analysis.clientQuestionMessage)

        val style = resolveStyle(project)
        val formal = style == ClientAddressStyle.FORMAL
        val intro = analysis.clientQuestionIntro
            .takeIf { it.isNotBlank() }
            ?.let(::normalizeSentence)
            ?: fallbackIntro(questions.first())
        val closing = analysis.clientQuestionClosing
            .takeIf { it.isNotBlank() }
            ?.let(::normalizeSentence)
            ?: fallbackClosing(questions.first())
        val firstLine = opening(project.clientName.trim(), style, adaptPronoun(intro, formal))
        val numbered = questions
            .mapIndexed { index, question -> "${index + 1}. ${adaptPronoun(question, formal)}" }
            .joinToString("\n")
        val finalLine = adaptPronoun(closing, formal)
        return listOf(firstLine, "", numbered, "", finalLine).joinToString("\n")
    }

    private fun opening(name: String, style: ClientAddressStyle, intro: String): String = when (style) {
        ClientAddressStyle.STUDENT -> {
            val cleanName = name.trimEnd('，', ',')
            val address = if (cleanName.isBlank()) {
                "同学"
            } else if (cleanName.endsWith("同学")) {
                cleanName
            } else {
                "${cleanName}同学"
            }
            "$address，$intro"
        }
        ClientAddressStyle.INFORMAL -> {
            val cleanName = name.trimEnd('，', ',')
            if (cleanName.isBlank()) "你好，$intro" else "$cleanName，$intro"
        }
        ClientAddressStyle.FORMAL -> {
            val cleanName = name.trimEnd('，', ',')
            if (cleanName.isBlank()) "您好。$intro" else "$cleanName，您好。$intro"
        }
        ClientAddressStyle.AUTO -> error("AUTO should be resolved before formatting")
    }

    private fun resolveStyle(project: ResumeProject): ClientAddressStyle {
        if (project.clientAddressStyle != ClientAddressStyle.AUTO) {
            return project.clientAddressStyle
        }
        return when (project.seniorityLevel) {
            SeniorityLevel.ENTRY, SeniorityLevel.JUNIOR -> ClientAddressStyle.STUDENT
            SeniorityLevel.SENIOR, SeniorityLevel.LEAD, SeniorityLevel.MANAGER ->
                ClientAddressStyle.FORMAL
            SeniorityLevel.AUTO, SeniorityLevel.MID -> ClientAddressStyle.INFORMAL
        }
    }

    private fun fallbackIntro(question: String): String = when {
        question.contains("项目") -> "简历里有几处项目经历还需要和你确认一下。"
        question.contains("时间") -> "简历里有几处经历时间还需要和你核对一下。"
        question.contains("职责") || question.contains("负责") ->
            "简历里有几处工作内容还需要和你确认一下。"
        question.contains("技能") || question.contains("工具") ->
            "简历里有几处技能描述还需要和你确认一下。"
        else -> "简历里有几处信息还需要和你确认一下。"
    }

    private fun fallbackClosing(question: String): String = if (
        Regex("多少|人数|数量|规模|比例|百分比|金额").containsMatchIn(question)
    ) {
        "按编号回复就行，大概范围也可以，不确定的地方写“不确定”。"
    } else {
        "按编号回复就行，暂时不确定的地方写“不确定”也可以。"
    }

    private fun normalizeQuestion(value: String): String {
        val cleaned = value
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("^\\d+\\s*[.、)）]\\s*"), "")
            .trimEnd('。', '；', ';', '！', '!')
        return when {
            cleaned.isBlank() -> ""
            cleaned.endsWith("？") || cleaned.endsWith("?") -> cleaned
            else -> "$cleaned。"
        }
    }

    private fun normalizeSentence(value: String): String {
        val cleaned = value
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('。', '；', ';', '！', '!')
        return if (cleaned.isBlank()) "" else "$cleaned。"
    }

    private fun normalizeBlock(value: String): String = value
        .replace("\r\n", "\n")
        .lines()
        .joinToString("\n") { it.trim() }
        .trim()

    private fun adaptPronoun(value: String, formal: Boolean): String =
        if (formal) value.replace("你们", "各位").replace("你", "您") else value.replace("您", "你")
}
