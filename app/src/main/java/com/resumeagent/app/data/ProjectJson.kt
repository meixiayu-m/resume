package com.resumeagent.app.data

import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.IssueSeverity
import com.resumeagent.app.domain.ProjectStage
import com.resumeagent.app.domain.ResumeAnalysis
import com.resumeagent.app.domain.ResumeIssue
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.domain.ScoreDimension
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
        .put("resumeFileName", project.resumeFileName)
        .put("localResumePath", project.localResumePath)
        .put("resumeText", project.resumeText)
        .put("jobDescription", project.jobDescription)
        .put("supplements", project.supplements)
        .put("analysis", project.analysis?.let(::encodeAnalysis))
        .put("draftSections", JSONArray().apply {
            project.draftSections.forEach { put(encodeSection(it)) }
        })
        .put("verificationChecklist", JSONArray(project.verificationChecklist))
        .put("traceability", JSONArray().apply {
            project.traceability.forEach { put(encodeTraceability(it)) }
        })
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
            resumeFileName = json.optString("resumeFileName"),
            localResumePath = json.optString("localResumePath").takeIf { it.isNotBlank() && it != "null" },
            resumeText = json.optString("resumeText"),
            jobDescription = json.optString("jobDescription"),
            supplements = json.optString("supplements"),
            analysis = json.optJSONObject("analysis")?.let(::decodeAnalysis),
            draftSections = json.optJSONArray("draftSections").toObjectList(::decodeSection),
            verificationChecklist = json.optJSONArray("verificationChecklist").toStringList(),
            traceability = json.optJSONArray("traceability").toObjectList(::decodeTraceability),
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
        riskWarnings = json.optJSONArray("riskWarnings").toStringList()
    )

    fun encodeSection(section: DraftSection): JSONObject = JSONObject()
        .put("id", section.id)
        .put("title", section.title)
        .put("content", section.content)
        .put("changeNote", section.changeNote)
        .put("sourceEvidence", section.sourceEvidence)
        .put("verificationStatus", section.verificationStatus)
        .put("accepted", section.accepted)

    fun decodeSection(json: JSONObject): DraftSection = DraftSection(
        id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        title = json.optString("title"),
        content = json.optString("content"),
        changeNote = json.optString("changeNote"),
        sourceEvidence = json.optString("sourceEvidence"),
        verificationStatus = json.optString("verificationStatus"),
        accepted = json.optBoolean("accepted", true)
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
