package com.resumeagent.app.domain

import java.util.UUID

enum class ProjectStage {
    IMPORTED,
    ANALYZED,
    DRAFTED,
    READY
}

enum class IssueSeverity {
    CRITICAL,
    WARNING,
    INFO
}

data class ScoreDimension(
    val name: String,
    val score: Int,
    val detail: String
)

data class ResumeIssue(
    val severity: IssueSeverity,
    val title: String,
    val evidence: String,
    val advice: String
)

data class ResumeAnalysis(
    val overallScore: Int,
    val fitSummary: String,
    val dimensions: List<ScoreDimension>,
    val strengths: List<String>,
    val issues: List<ResumeIssue>,
    val missingQuestions: List<String>,
    val jobKeywords: List<String>,
    val clientQuestionMessage: String = "",
    val riskWarnings: List<String> = emptyList()
)

data class DraftSection(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val changeNote: String = "",
    val sourceEvidence: String = "",
    val verificationStatus: String = "",
    val accepted: Boolean = true
)

data class TraceabilityEntry(
    val fact: String,
    val rewrittenText: String,
    val sourceEvidence: String,
    val status: String = "待核实"
)

data class ResumeProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val company: String = "",
    val targetRole: String,
    val industry: String = "",
    val resumeFileName: String = "",
    val localResumePath: String? = null,
    val resumeText: String,
    val jobDescription: String,
    val supplements: String = "",
    val analysis: ResumeAnalysis? = null,
    val draftSections: List<DraftSection> = emptyList(),
    val verificationChecklist: List<String> = emptyList(),
    val traceability: List<TraceabilityEntry> = emptyList(),
    val stage: ProjectStage = ProjectStage.IMPORTED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val acceptedSections: List<DraftSection>
        get() = draftSections.filter { it.accepted }
}

enum class AiProviderId {
    ZHIPU,
    KIMI,
    DEEPSEEK,
    QWEN,
    CUSTOM
}

data class AiProviderSettings(
    val id: AiProviderId,
    val label: String,
    val endpoint: String,
    val textModel: String,
    val visionModel: String = ""
)

data class AiSettings(
    val providers: List<AiProviderSettings> = defaultAiProviders(),
    val activeTextProvider: AiProviderId = AiProviderId.ZHIPU,
    val activeVisionProvider: AiProviderId = AiProviderId.ZHIPU
)

fun defaultAiProviders(): List<AiProviderSettings> = listOf(
    AiProviderSettings(
        id = AiProviderId.ZHIPU,
        label = "智谱",
        endpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        textModel = "glm-4.6-flash",
        visionModel = "glm-4.6v-flash"
    ),
    AiProviderSettings(
        id = AiProviderId.KIMI,
        label = "Kimi",
        endpoint = "https://api.moonshot.cn/v1/chat/completions",
        textModel = "moonshot-v1-32k",
        visionModel = "moonshot-v1-8k-vision-preview"
    ),
    AiProviderSettings(
        id = AiProviderId.DEEPSEEK,
        label = "DeepSeek",
        endpoint = "https://api.deepseek.com/chat/completions",
        textModel = "deepseek-chat",
        visionModel = ""
    ),
    AiProviderSettings(
        id = AiProviderId.QWEN,
        label = "千问",
        endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        textModel = "qwen-plus",
        visionModel = "qwen-vl-max"
    ),
    AiProviderSettings(
        id = AiProviderId.CUSTOM,
        label = "自定义",
        endpoint = "",
        textModel = "",
        visionModel = ""
    )
)

data class ImportedDocument(
    val displayName: String,
    val localPath: String,
    val text: String,
    val needsOcr: Boolean = false
)
