package com.resumeagent.app.domain

import java.util.UUID

enum class ProjectStage {
    IMPORTED,
    ANALYZED,
    DRAFTED,
    READY
}

enum class OptimizationMode {
    TARGETED,
    ROLE_ONLY,
    GENERAL
}

enum class SeniorityLevel {
    AUTO,
    ENTRY,
    JUNIOR,
    MID,
    SENIOR,
    LEAD,
    MANAGER
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
    val shouldReorderSections: Boolean = false,
    val recommendedSectionOrder: List<String> = emptyList(),
    val sectionOrderAdvice: String = "",
    val riskWarnings: List<String> = emptyList()
)

data class DraftSection(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val changeNote: String = "",
    val benefit: String = "",
    val originalContent: String = "",
    val sourceEvidence: String = "",
    val verificationStatus: String = "",
    val accepted: Boolean = true,
    val keepOriginal: Boolean = false
)

data class TraceabilityEntry(
    val fact: String,
    val rewrittenText: String,
    val sourceEvidence: String,
    val status: String = "待核实"
)

data class FactClaim(
    val id: String = UUID.randomUUID().toString(),
    val quote: String,
    val kind: String = "经历",
    val verified: Boolean = false
)

data class RuleCheck(
    val name: String,
    val passed: Boolean,
    val detail: String
)

enum class ParagraphAction {
    KEEP,
    CHANGE,
    DELETE
}

data class ParagraphRevision(
    val index: Int,
    val original: String,
    val revised: String = "",
    val action: ParagraphAction = ParagraphAction.KEEP,
    val reason: String = "",
    val benefit: String = ""
)

data class ProjectVersion(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val instruction: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val factLedger: List<FactClaim> = emptyList(),
    val ruleChecks: List<RuleCheck> = emptyList(),
    val analysis: ResumeAnalysis? = null,
    val draftSections: List<DraftSection> = emptyList(),
    val verificationChecklist: List<String> = emptyList(),
    val traceability: List<TraceabilityEntry> = emptyList(),
    val secondReviewFindings: List<String> = emptyList(),
    val secondReviewProvider: String = "",
    val layoutRevision: List<ParagraphRevision> = emptyList(),
    val sectionOrderApplied: Boolean = false
)

data class ProjectInput(
    val title: String = "",
    val clientName: String = "",
    val dueDate: String = "",
    val orderNote: String = "",
    val targetRole: String = "",
    val company: String = "",
    val industry: String = "",
    val optimizationMode: OptimizationMode = OptimizationMode.TARGETED,
    val seniorityLevel: SeniorityLevel = SeniorityLevel.AUTO,
    val resumeText: String = "",
    val jobDescription: String = "",
    val supplements: String = ""
)

data class ResumeProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val company: String = "",
    val targetRole: String = "",
    val industry: String = "",
    val clientName: String = "",
    val dueDate: String = "",
    val orderNote: String = "",
    val optimizationMode: OptimizationMode = OptimizationMode.TARGETED,
    val seniorityLevel: SeniorityLevel = SeniorityLevel.AUTO,
    val resumeFileName: String = "",
    val localResumePath: String? = null,
    val resumeText: String,
    val jobDescriptionFileName: String = "",
    val localJobDescriptionPath: String? = null,
    val jobDescription: String = "",
    val supplements: String = "",
    val factLedger: List<FactClaim> = emptyList(),
    val ruleChecks: List<RuleCheck> = emptyList(),
    val analysis: ResumeAnalysis? = null,
    val draftSections: List<DraftSection> = emptyList(),
    val verificationChecklist: List<String> = emptyList(),
    val traceability: List<TraceabilityEntry> = emptyList(),
    val secondReviewFindings: List<String> = emptyList(),
    val secondReviewProvider: String = "",
    val layoutRevision: List<ParagraphRevision> = emptyList(),
    val sectionOrderApplied: Boolean = false,
    val versions: List<ProjectVersion> = emptyList(),
    val activeVersionId: String? = null,
    val finalVersionId: String? = null,
    val stage: ProjectStage = ProjectStage.IMPORTED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val acceptedSections: List<DraftSection>
        get() = draftSections.filter { it.accepted }.map { section ->
            if (section.keepOriginal) {
                section.copy(
                    content = section.originalContent.ifBlank { section.sourceEvidence }
                )
            } else {
                section
            }
        }
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
    val modelsEndpoint: String,
    val textModel: String,
    val visionModel: String = ""
)

data class AiSettings(
    val providers: List<AiProviderSettings> = defaultAiProviders(),
    val activeTextProvider: AiProviderId = AiProviderId.ZHIPU,
    val activeVisionProvider: AiProviderId = AiProviderId.ZHIPU,
    val dualReviewEnabled: Boolean = false,
    val activeReviewProvider: AiProviderId = AiProviderId.ZHIPU
)

fun defaultAiProviders(): List<AiProviderSettings> = listOf(
    AiProviderSettings(
        id = AiProviderId.ZHIPU,
        label = "智谱",
        endpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        modelsEndpoint = "https://open.bigmodel.cn/api/paas/v4/models",
        textModel = "glm-4.6-flash",
        visionModel = "glm-4.6v-flash"
    ),
    AiProviderSettings(
        id = AiProviderId.KIMI,
        label = "Kimi",
        endpoint = "https://api.moonshot.cn/v1/chat/completions",
        modelsEndpoint = "https://api.moonshot.cn/v1/models",
        textModel = "moonshot-v1-32k",
        visionModel = "moonshot-v1-8k-vision-preview"
    ),
    AiProviderSettings(
        id = AiProviderId.DEEPSEEK,
        label = "DeepSeek",
        endpoint = "https://api.deepseek.com/chat/completions",
        modelsEndpoint = "https://api.deepseek.com/models",
        textModel = "deepseek-chat",
        visionModel = ""
    ),
    AiProviderSettings(
        id = AiProviderId.QWEN,
        label = "千问",
        endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        modelsEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/models",
        textModel = "qwen-plus",
        visionModel = "qwen-vl-max"
    ),
    AiProviderSettings(
        id = AiProviderId.CUSTOM,
        label = "自定义",
        endpoint = "",
        modelsEndpoint = "",
        textModel = "",
        visionModel = ""
    )
)

data class AiModelInfo(
    val id: String,
    val ownedBy: String = "",
    val contextLength: Long? = null,
    val description: String = ""
)

data class ImportedDocument(
    val displayName: String,
    val localPath: String,
    val text: String,
    val needsOcr: Boolean = false
)
