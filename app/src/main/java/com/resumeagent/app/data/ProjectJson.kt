package com.resumeagent.app.data

import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.ClientAddressStyle
import com.resumeagent.app.domain.FactClaim
import com.resumeagent.app.domain.IssueSeverity
import com.resumeagent.app.domain.OptimizationMode
import com.resumeagent.app.domain.ParagraphAction
import com.resumeagent.app.domain.ParagraphRevision
import com.resumeagent.app.domain.ProjectStage
import com.resumeagent.app.domain.ProjectVersion
import com.resumeagent.app.domain.ResumeAnalysis
import com.resumeagent.app.domain.ResumeIssue
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.domain.RuleCheck
import com.resumeagent.app.domain.ScoreDimension
import com.resumeagent.app.domain.SeniorityLevel
import com.resumeagent.app.domain.TraceabilityEntry
import org.json.JSONArray
import org.json.JSONObject

object ProjectJson {
    fun encode(project: ResumeProject): String = JSONObject()
        .put("id", project.id)
        .put("title", project.title)
        .put("company", project.company)
        .put("targetRole", project.targetRole)
        .put("industry", project.industry)
        .put("clientName", project.clientName)
        .put("dueDate", project.dueDate)
        .put("orderNote", project.orderNote)
        .put("clientAddressStyle", project.clientAddressStyle.name)
        .put("optimizationMode", project.optimizationMode.name)
        .put("seniorityLevel", project.seniorityLevel.name)
        .put("resumeFileName", project.resumeFileName)
        .put("localResumePath", project.localResumePath)
        .put("resumeText", project.resumeText)
        .put("jobDescriptionFileName", project.jobDescriptionFileName)
        .put("localJobDescriptionPath", project.localJobDescriptionPath)
        .put("jobDescription", project.jobDescription)
        .put("supplements", project.supplements)
        .put("factLedger", JSONArray().apply {
            project.factLedger.forEach { put(encodeFact(it)) }
        })
        .put("ruleChecks", JSONArray().apply {
            project.ruleChecks.forEach { put(encodeRuleCheck(it)) }
        })
        .put("analysis", project.analysis?.let(::encodeAnalysis))
        .put("draftSections", JSONArray().apply {
            project.draftSections.forEach { put(encodeSection(it)) }
        })
        .put("verificationChecklist", JSONArray(project.verificationChecklist))
        .put("traceability", JSONArray().apply {
            project.traceability.forEach { put(encodeTraceability(it)) }
        })
        .put("secondReviewFindings", JSONArray(project.secondReviewFindings))
        .put("secondReviewProvider", project.secondReviewProvider)
        .put("layoutRevision", JSONArray().apply {
            project.layoutRevision.forEach { put(encodeParagraphRevision(it)) }
        })
        .put("sectionOrderApplied", project.sectionOrderApplied)
        .put("versions", JSONArray().apply {
            project.versions.forEach { put(encodeVersion(it)) }
        })
        .put("activeVersionId", project.activeVersionId)
        .put("finalVersionId", project.finalVersionId)
        .put("stage", project.stage.name)
        .put("createdAt", project.createdAt)
        .put("updatedAt", project.updatedAt)
        .toString()

    fun decode(raw: String): ResumeProject {
        val json = JSONObject(raw)
        return ResumeProject(
            id = json.getString("id"),
            title = json.optString("title"),
            company = json.optString("company"),
            targetRole = json.optString("targetRole"),
            industry = json.optString("industry"),
            clientName = json.optString("clientName"),
            dueDate = json.optString("dueDate"),
            orderNote = json.optString("orderNote"),
            clientAddressStyle = json.optString("clientAddressStyle")
                .toEnum(ClientAddressStyle.AUTO),
            optimizationMode = json.optString("optimizationMode")
                .toEnum(OptimizationMode.TARGETED),
            seniorityLevel = json.optString("seniorityLevel")
                .toEnum(SeniorityLevel.AUTO),
            resumeFileName = json.optString("resumeFileName"),
            localResumePath = json.optString("localResumePath").takeIf { it.isNotBlank() && it != "null" },
            resumeText = json.optString("resumeText"),
            jobDescriptionFileName = json.optString("jobDescriptionFileName"),
            localJobDescriptionPath = json.optString("localJobDescriptionPath")
                .takeIf { it.isNotBlank() && it != "null" },
            jobDescription = json.optString("jobDescription"),
            supplements = json.optString("supplements"),
            factLedger = json.optJSONArray("factLedger").toObjectList(::decodeFact),
            ruleChecks = json.optJSONArray("ruleChecks").toObjectList(::decodeRuleCheck),
            analysis = json.optJSONObject("analysis")?.let(::decodeAnalysis),
            draftSections = json.optJSONArray("draftSections").toObjectList(::decodeSection),
            verificationChecklist = json.optJSONArray("verificationChecklist").toStringList(),
            traceability = json.optJSONArray("traceability").toObjectList(::decodeTraceability),
            secondReviewFindings = json.optJSONArray("secondReviewFindings").toStringList(),
            secondReviewProvider = json.optString("secondReviewProvider"),
            layoutRevision = json.optJSONArray("layoutRevision")
                .toObjectList(::decodeParagraphRevision),
            sectionOrderApplied = json.optBoolean("sectionOrderApplied"),
            versions = json.optJSONArray("versions").toObjectList(::decodeVersion),
            activeVersionId = json.optString("activeVersionId")
                .takeIf { it.isNotBlank() && it != "null" },
            finalVersionId = json.optString("finalVersionId")
                .takeIf { it.isNotBlank() && it != "null" },
            stage = runCatching { ProjectStage.valueOf(json.optString("stage")) }
                .getOrDefault(ProjectStage.IMPORTED),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    fun encodeAnalysis(analysis: ResumeAnalysis): JSONObject = JSONObject()
        .put("overallScore", analysis.overallScore)
        .put("fitSummary", analysis.fitSummary)
        .put("dimensions", JSONArray().apply {
            analysis.dimensions.forEach {
                put(JSONObject().put("name", it.name).put("score", it.score).put("detail", it.detail))
            }
        })
        .put("strengths", JSONArray(analysis.strengths))
        .put("issues", JSONArray().apply {
            analysis.issues.forEach {
                put(JSONObject()
                    .put("severity", it.severity.name)
                    .put("title", it.title)
                    .put("evidence", it.evidence)
                    .put("advice", it.advice))
            }
        })
        .put("missingQuestions", JSONArray(analysis.missingQuestions))
        .put("jobKeywords", JSONArray(analysis.jobKeywords))
        .put("clientQuestionMessage", analysis.clientQuestionMessage)
        .put("clientQuestionIntro", analysis.clientQuestionIntro)
        .put("clientQuestionClosing", analysis.clientQuestionClosing)
        .put("shouldReorderSections", analysis.shouldReorderSections)
        .put("recommendedSectionOrder", JSONArray(analysis.recommendedSectionOrder))
        .put("sectionOrderAdvice", analysis.sectionOrderAdvice)
        .put("riskWarnings", JSONArray(analysis.riskWarnings))

    fun decodeAnalysis(json: JSONObject): ResumeAnalysis = ResumeAnalysis(
        overallScore = json.optInt("overallScore").coerceIn(0, 100),
        fitSummary = json.optString("fitSummary"),
        dimensions = json.optJSONArray("dimensions").toObjectList {
            ScoreDimension(
                name = it.optString("name"),
                score = it.optInt("score").coerceIn(0, 100),
                detail = it.optString("detail")
            )
        },
        strengths = json.optJSONArray("strengths").toStringList(),
        issues = json.optJSONArray("issues").toObjectList {
            ResumeIssue(
                severity = runCatching { IssueSeverity.valueOf(it.optString("severity")) }
                    .getOrDefault(IssueSeverity.WARNING),
                title = it.optString("title"),
                evidence = it.optString("evidence"),
                advice = it.optString("advice")
            )
        },
        missingQuestions = json.optJSONArray("missingQuestions").toStringList(),
        jobKeywords = json.optJSONArray("jobKeywords").toStringList(),
        clientQuestionMessage = json.optString("clientQuestionMessage"),
        clientQuestionIntro = json.optString("clientQuestionIntro"),
        clientQuestionClosing = json.optString("clientQuestionClosing"),
        shouldReorderSections = json.optBoolean("shouldReorderSections"),
        recommendedSectionOrder = json.optJSONArray("recommendedSectionOrder").toStringList(),
        sectionOrderAdvice = json.optString("sectionOrderAdvice"),
        riskWarnings = json.optJSONArray("riskWarnings").toStringList()
    )

    fun encodeSection(section: DraftSection): JSONObject = JSONObject()
        .put("id", section.id)
        .put("title", section.title)
        .put("content", section.content)
        .put("changeNote", section.changeNote)
        .put("benefit", section.benefit)
        .put("originalContent", section.originalContent)
        .put("sourceEvidence", section.sourceEvidence)
        .put("verificationStatus", section.verificationStatus)
        .put("accepted", section.accepted)
        .put("keepOriginal", section.keepOriginal)

    fun decodeSection(json: JSONObject): DraftSection = DraftSection(
        id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        title = json.optString("title"),
        content = json.optString("content"),
        changeNote = json.optString("changeNote"),
        benefit = json.optString("benefit"),
        originalContent = json.optString("originalContent"),
        sourceEvidence = json.optString("sourceEvidence"),
        verificationStatus = json.optString("verificationStatus"),
        accepted = json.optBoolean("accepted", true),
        keepOriginal = json.optBoolean("keepOriginal", false)
    )

    fun encodeTraceability(entry: TraceabilityEntry): JSONObject = JSONObject()
        .put("fact", entry.fact)
        .put("rewrittenText", entry.rewrittenText)
        .put("sourceEvidence", entry.sourceEvidence)
        .put("status", entry.status)

    fun decodeTraceability(json: JSONObject): TraceabilityEntry = TraceabilityEntry(
        fact = json.optString("fact"),
        rewrittenText = json.optString("rewrittenText"),
        sourceEvidence = json.optString("sourceEvidence"),
        status = json.optString("status").ifBlank { "待核实" }
    )

    fun encodeFact(claim: FactClaim): JSONObject = JSONObject()
        .put("id", claim.id)
        .put("quote", claim.quote)
        .put("kind", claim.kind)
        .put("verified", claim.verified)

    fun decodeFact(json: JSONObject): FactClaim = FactClaim(
        id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        quote = json.optString("quote"),
        kind = json.optString("kind").ifBlank { "经历" },
        verified = json.optBoolean("verified", false)
    )

    fun encodeRuleCheck(check: RuleCheck): JSONObject = JSONObject()
        .put("name", check.name)
        .put("passed", check.passed)
        .put("detail", check.detail)

    fun decodeRuleCheck(json: JSONObject): RuleCheck = RuleCheck(
        name = json.optString("name"),
        passed = json.optBoolean("passed", false),
        detail = json.optString("detail")
    )

    fun encodeVersion(version: ProjectVersion): JSONObject = JSONObject()
        .put("id", version.id)
        .put("label", version.label)
        .put("instruction", version.instruction)
        .put("createdAt", version.createdAt)
        .put("factLedger", JSONArray().apply {
            version.factLedger.forEach { put(encodeFact(it)) }
        })
        .put("ruleChecks", JSONArray().apply {
            version.ruleChecks.forEach { put(encodeRuleCheck(it)) }
        })
        .put("analysis", version.analysis?.let(::encodeAnalysis))
        .put("draftSections", JSONArray().apply {
            version.draftSections.forEach { put(encodeSection(it)) }
        })
        .put("verificationChecklist", JSONArray(version.verificationChecklist))
        .put("traceability", JSONArray().apply {
            version.traceability.forEach { put(encodeTraceability(it)) }
        })
        .put("secondReviewFindings", JSONArray(version.secondReviewFindings))
        .put("secondReviewProvider", version.secondReviewProvider)
        .put("layoutRevision", JSONArray().apply {
            version.layoutRevision.forEach { put(encodeParagraphRevision(it)) }
        })
        .put("sectionOrderApplied", version.sectionOrderApplied)

    fun decodeVersion(json: JSONObject): ProjectVersion = ProjectVersion(
        id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        label = json.optString("label"),
        instruction = json.optString("instruction"),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        factLedger = json.optJSONArray("factLedger").toObjectList(::decodeFact),
        ruleChecks = json.optJSONArray("ruleChecks").toObjectList(::decodeRuleCheck),
        analysis = json.optJSONObject("analysis")?.let(::decodeAnalysis),
        draftSections = json.optJSONArray("draftSections").toObjectList(::decodeSection),
        verificationChecklist = json.optJSONArray("verificationChecklist").toStringList(),
        traceability = json.optJSONArray("traceability").toObjectList(::decodeTraceability),
        secondReviewFindings = json.optJSONArray("secondReviewFindings").toStringList(),
        secondReviewProvider = json.optString("secondReviewProvider"),
        layoutRevision = json.optJSONArray("layoutRevision")
            .toObjectList(::decodeParagraphRevision),
        sectionOrderApplied = json.optBoolean("sectionOrderApplied")
    )

    fun encodeParagraphRevision(revision: ParagraphRevision): JSONObject = JSONObject()
        .put("index", revision.index)
        .put("original", revision.original)
        .put("revised", revision.revised)
        .put("action", revision.action.name)
        .put("reason", revision.reason)
        .put("benefit", revision.benefit)

    fun decodeParagraphRevision(json: JSONObject): ParagraphRevision = ParagraphRevision(
        index = json.optInt("index"),
        original = json.optString("original"),
        revised = json.optString("revised"),
        action = runCatching { ParagraphAction.valueOf(json.optString("action")) }
            .getOrDefault(ParagraphAction.KEEP),
        reason = json.optString("reason"),
        benefit = json.optString("benefit")
    )

    private inline fun <reified T : Enum<T>> String.toEnum(fallback: T): T =
        runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)

    private fun <T> JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let { add(mapper(it)) }
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
