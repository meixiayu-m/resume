package com.resumeagent.app.domain

data class FactAuditResult(
    val sections: List<DraftSection>,
    val verificationChecklist: List<String>,
    val traceability: List<TraceabilityEntry>
)

/**
 * AI 负责语义检查，这一层负责拦截最容易出现的客观失真：
 * 新增数字、职级/负责程度升级，以及原简历没有依据的 JD 关键词。
 * 发现风险时只标记待核实，不擅自补造证据。
 */
object FactGuard {
    private val numberPattern = Regex("""(?<![A-Za-z0-9.])\d+(?:\.\d+)?%?(?![A-Za-z0-9.])""")
    private val emojiPattern = Regex("""[\p{So}\p{Sk}\uFE0F\u200D]+""")
    private val promotionPattern = Regex("主导|牵头|独立完成|全面负责|负责")
    private val juniorActionPattern = Regex("参与|协助|配合")

    fun audit(
        project: ResumeProject,
        rawSections: List<DraftSection>,
        aiChecklist: List<String>,
        aiTraceability: List<TraceabilityEntry>
    ): FactAuditResult {
        val source = "${project.resumeText}\n${project.supplements}"
        val normalizedSource = normalizeEvidence(source)
        val sourceNumbers = numberTokens(source)
        val checklist = linkedSetOf<String>()
        aiChecklist.map(::sanitize).filterTo(checklist) { it.isNotBlank() }
        val traceability = aiTraceability.map {
            it.copy(
                fact = sanitize(it.fact),
                rewrittenText = sanitize(it.rewrittenText),
                sourceEvidence = sanitize(it.sourceEvidence),
                status = sanitize(it.status).ifBlank { "待核实" }
            )
        }.toMutableList()

        val auditedSections = rawSections.map { section ->
            val content = sanitize(section.content)
            val sourceEvidence = sanitize(section.sourceEvidence)
            val risks = mutableListOf<String>()
            val evidenceVerified =
                sourceEvidence.isNotBlank() && normalizedSource.contains(normalizeEvidence(sourceEvidence))

            (numberTokens(content) - sourceNumbers).forEach { number ->
                risks += "新增数字 $number"
                checklist += "核实数字“$number”是否在原简历或客户补充中有明确依据"
                traceability += TraceabilityEntry(
                    fact = "数字 $number",
                    rewrittenText = number,
                    sourceEvidence = sourceEvidence,
                    status = "待核实"
                )
            }

            val localEvidence = sourceEvidence.ifBlank { source }
            if (promotionPattern.containsMatchIn(content)) {
                val promotedTerms = promotionPattern.findAll(content)
                    .map { it.value }
                    .distinct()
                    .filterNot { localEvidence.contains(it) }
                    .toList()
                if (promotedTerms.isNotEmpty() ||
                    (juniorActionPattern.containsMatchIn(localEvidence) &&
                        promotionPattern.containsMatchIn(content))
                ) {
                    val displayedTerms = promotedTerms.ifEmpty {
                        promotionPattern.findAll(content).map { it.value }.distinct().toList()
                    }
                    risks += "负责程度可能升级：${displayedTerms.joinToString("、")}"
                    checklist += "核实修订稿中的“${displayedTerms.joinToString("、")}”是否有该段原文依据；若原文仅为参与、协助或配合，应恢复原职责边界"
                    traceability += TraceabilityEntry(
                        fact = "职责边界",
                        rewrittenText = displayedTerms.joinToString("、"),
                        sourceEvidence = sourceEvidence,
                        status = "待核实"
                    )
                }
            }

            val unsupportedKeywords = project.analysis?.jobKeywords.orEmpty().filter { keyword ->
                keyword.isNotBlank() &&
                    !source.contains(keyword, ignoreCase = true) &&
                    content.contains(keyword, ignoreCase = true)
            }
            unsupportedKeywords.forEach { keyword ->
                risks += "原简历没有依据的岗位关键词：$keyword"
                checklist += "核实岗位关键词“$keyword”是否确实由客户掌握或使用过；禁止仅为匹配 JD 而添加"
                traceability += TraceabilityEntry(
                    fact = "技能或关键词 $keyword",
                    rewrittenText = keyword,
                    sourceEvidence = sourceEvidence,
                    status = "待核实"
                )
            }

            if (sourceEvidence.isBlank() && content.length > 24) {
                risks += "缺少段落级原文依据"
                checklist += "核实章节“${section.title}”的事实来源；未提供段落级依据时不得直接采用"
                traceability += TraceabilityEntry(
                    fact = section.title,
                    rewrittenText = content.take(180),
                    sourceEvidence = "",
                    status = "待核实"
                )
            } else if (sourceEvidence.isNotBlank() && !evidenceVerified) {
                risks += "原文依据未在原始材料中找到"
                checklist += "核实章节“${section.title}”引用的原文依据是否真实存在；当前依据与客户原始材料不能逐字对应"
                traceability += TraceabilityEntry(
                    fact = section.title,
                    rewrittenText = content.take(180),
                    sourceEvidence = sourceEvidence,
                    status = "依据无法匹配"
                )
            }

            if (sourceEvidence.isNotBlank()) {
                traceability += TraceabilityEntry(
                    fact = "章节：${section.title}",
                    rewrittenText = content.take(180),
                    sourceEvidence = sourceEvidence,
                    status = if (risks.isEmpty()) "有原文依据" else "待核实"
                )
            }

            section.copy(
                content = content,
                changeNote = sanitize(section.changeNote),
                benefit = sanitize(section.benefit),
                sourceEvidence = sourceEvidence,
                verificationStatus = risks.joinToString("；"),
                accepted = section.accepted && risks.isEmpty()
            )
        }

        return FactAuditResult(
            sections = auditedSections,
            verificationChecklist = checklist.toList(),
            traceability = traceability.distinctBy {
                listOf(it.fact, it.rewrittenText, it.sourceEvidence, it.status)
            }
        )
    }

    fun sanitize(value: String): String = value
        .replace(emojiPattern, "")
        .replace(Regex("[!！]+"), "。")
        .replace(Regex("[。]{2,}"), "。")
        .trim()

    private fun numberTokens(value: String): Set<String> =
        numberPattern.findAll(value).map { it.value }.toSet()

    private fun normalizeEvidence(value: String): String =
        value.lowercase().replace(Regex("""[\s，。；：、,.!?！？]+"""), "")
}
