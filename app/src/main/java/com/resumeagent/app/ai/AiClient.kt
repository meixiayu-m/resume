package com.resumeagent.app.ai

import android.util.Base64
import com.resumeagent.app.data.ProjectJson
import com.resumeagent.app.data.SecureSettings
import com.resumeagent.app.domain.AiProviderSettings
import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.FactAuditResult
import com.resumeagent.app.domain.FactClaim
import com.resumeagent.app.domain.FactGuard
import com.resumeagent.app.domain.OptimizationMode
import com.resumeagent.app.domain.ResumeAnalysis
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.domain.SeniorityLevel
import com.resumeagent.app.domain.TraceabilityEntry
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AiClient(private val settings: SecureSettings) {
    suspend fun analyze(project: ResumeProject): ResumeAnalysis {
        val facts = if (project.factLedger.isEmpty()) extractFacts(project) else project.factLedger
        val response = complete(
            kind = RequestKind.TEXT,
            systemPrompt = analysisSystemPrompt(project),
            userContent = buildProjectContext(project, facts)
        )
        val json = runCatching { AiResultParser.objectFrom(response) }
            .getOrElse { error("AI 返回的诊断结果无法解析，请重试或更换文本模型") }
        val decoded = ProjectJson.decodeAnalysis(json)
        val analysis = if (project.optimizationMode == OptimizationMode.GENERAL) {
            decoded.copy(jobKeywords = emptyList())
        } else {
            decoded
        }
        require(analysis.fitSummary.isNotBlank()) { "AI 没有返回适配度结论" }
        require(analysis.dimensions.isNotEmpty()) { "AI 没有返回诊断维度" }
        return analysis
    }

    suspend fun extractFacts(project: ResumeProject): List<FactClaim> {
        val response = complete(
            kind = RequestKind.TEXT,
            systemPrompt = FACT_EXTRACTION_PROMPT,
            userContent = """
原始简历：
${project.resumeText.take(MAX_RESUME_CHARS)}

客户补充信息：
${project.supplements.ifBlank { "暂无补充" }.take(MAX_SUPPLEMENT_CHARS)}
""".trimIndent()
        )
        val json = runCatching { AiResultParser.objectFrom(response) }
            .getOrElse { error("事实账本无法解析，请重试") }
        val array = json.optJSONArray("facts") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val quote = FactGuard.sanitize(item.optString("quote"))
                if (quote.isBlank()) continue
                add(
                    FactClaim(
                        id = item.optString("id").ifBlank { "F${index + 1}" },
                        quote = quote,
                        kind = item.optString("kind").ifBlank { "经历" },
                        verified = item.optBoolean("verified", false)
                    )
                )
            }
        }.take(120)
    }

    suspend fun rewrite(project: ResumeProject, revisionInstruction: String = ""): FactAuditResult {
        val analysis = project.analysis ?: error("请先完成简历诊断")
        val issues = analysis.issues.joinToString("\n") {
            "- [${it.severity}] ${it.title}：${it.advice}"
        }
        val response = complete(
            kind = RequestKind.TEXT,
            systemPrompt = rewriteSystemPrompt(project),
            userContent = """
${buildProjectContext(project, project.factLedger)}

诊断发现：
$issues

客户本轮返修要求：
${revisionInstruction.ifBlank { "无额外返修要求，按当前诊断完整生成新版本。" }}
""".trimIndent()
        )
        val json = runCatching { AiResultParser.objectFrom(response) }
            .getOrElse { error("AI 返回的改写结果无法解析，请重试或更换文本模型") }
        val array = json.optJSONArray("sections")
            ?: error("AI 返回缺少 sections 字段")
        val sections = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val content = FactGuard.sanitize(item.optString("content"))
                if (content.isBlank()) continue
                add(DraftSection(
                    title = FactGuard.sanitize(
                        item.optString("title").ifBlank { "简历段落 ${index + 1}" }
                    ),
                    content = content,
                    changeNote = FactGuard.sanitize(item.optString("changeNote")),
                    benefit = FactGuard.sanitize(item.optString("benefit")),
                    originalContent = FactGuard.sanitize(item.optString("originalContent")),
                    sourceEvidence = FactGuard.sanitize(item.optString("sourceEvidence"))
                ))
            }
        }
        require(sections.isNotEmpty()) { "AI 没有生成可用的简历段落" }

        val checklist = json.optJSONArray("verificationChecklist").toStringList()
        val traceability = json.optJSONArray("traceability").toTraceabilityList()
        return FactGuard.audit(project, sections, checklist, traceability)
    }

    suspend fun reviewDraft(
        project: ResumeProject,
        sections: List<DraftSection>
    ): List<String> {
        val config = settings.readAiSettings()
        val provider = config.providers.firstOrNull { it.id == config.activeReviewProvider }
            ?: error("审核 AI 服务不存在")
        val response = completeWithProvider(
            provider = provider,
            model = provider.textModel,
            kind = RequestKind.TEXT,
            systemPrompt = SECOND_REVIEW_PROMPT,
            userContent = """
${buildProjectContext(project, project.factLedger)}

待审核改写稿：
${sections.joinToString("\n\n") { "章节：${it.title}\n${it.content}\n原文依据：${it.sourceEvidence}" }}
""".trimIndent()
        )
        val json = AiResultParser.objectFrom(response)
        val findings = json.optJSONArray("findings") ?: JSONArray()
        val identityQuestion = Regex(
            "生日|出生日期|出生年月|身份证|手机号|电话号码|邮箱|住址|照片|性别|年龄|婚姻|婚育|民族"
        )
        return buildList {
            for (index in 0 until findings.length()) {
                findings.optString(index).trim()
                    .takeIf { it.isNotBlank() && !identityQuestion.containsMatchIn(it) }
                    ?.let(::add)
            }
        }
    }

    suspend fun ocrImages(images: List<ByteArray>): String {
        require(images.isNotEmpty()) { "没有可识别的文档页面" }
        require(images.size <= 8) { "一次最多识别 8 页" }
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put(
                "text",
                "逐页完整转录这些简历页面。保持标题、项目符号、日期和段落顺序；只输出转录文字，不要解释。"
            ))
        images.forEach { bytes ->
            content.put(JSONObject().put("type", "image_url").put(
                "image_url",
                JSONObject().put(
                    "url",
                    "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
                )
            ))
        }
        return complete(
            kind = RequestKind.VISION,
            systemPrompt = OCR_SYSTEM_PROMPT,
            userContent = content
        ).trim().replace(Regex("\\n{3,}"), "\n\n")
    }

    private fun analysisSystemPrompt(project: ResumeProject): String = buildString {
        append(ANALYSIS_BASE_PROMPT)
        append("\n\n")
        append(
            when (project.optimizationMode) {
                OptimizationMode.TARGETED -> ANALYSIS_TARGETED_INSTRUCTION
                OptimizationMode.ROLE_ONLY -> ANALYSIS_ROLE_ONLY_INSTRUCTION
                OptimizationMode.GENERAL -> ANALYSIS_GENERAL_INSTRUCTION
            }
        )
        append("\n\n")
        append(ANALYSIS_JSON_SCHEMA)
    }

    private fun rewriteSystemPrompt(project: ResumeProject): String = buildString {
        append(REWRITE_SYSTEM_PROMPT)
        append("\n\n")
        append(
            when (project.optimizationMode) {
                OptimizationMode.TARGETED ->
                    "当前是特定岗位模式。只能使用给定 JD 中明确出现的岗位要求，不得自行补充新的技术与职责。"
                OptimizationMode.ROLE_ONLY ->
                    "当前只有岗位方向，没有具体 JD。围绕该岗位方向整理已有事实，不得猜测公司招聘要求，不得虚构岗位关键词。"
                OptimizationMode.GENERAL ->
                    "当前没有目标岗位或 JD。目标是提升简历的真实性、清晰度和可读性，不进行关键词匹配，也不推测候选人想投什么岗位。"
            }
        )
    }

    private fun buildProjectContext(project: ResumeProject, facts: List<FactClaim>): String {
        val modeContext = when (project.optimizationMode) {
            OptimizationMode.TARGETED -> """
优化模式：特定岗位
目标岗位：${project.targetRole.ifBlank { "未填写" }}
目标公司：${project.company.ifBlank { "未填写" }}
目标行业：${project.industry.ifBlank { "未填写" }}
岗位 JD：
${project.jobDescription.take(MAX_JD_CHARS)}
""".trimIndent()
            OptimizationMode.ROLE_ONLY -> """
优化模式：只有岗位方向
目标岗位：${project.targetRole}
目标公司：未指定
目标行业：${project.industry.ifBlank { "未指定" }}
岗位 JD：未提供。禁止推测或补造 JD 要求。
""".trimIndent()
            OptimizationMode.GENERAL -> """
优化模式：通用简历优化
目标岗位：未指定
目标公司：未指定
目标行业：${project.industry.ifBlank { "未指定" }}
岗位 JD：未提供。不要进行岗位匹配或关键词覆盖判断。
""".trimIndent()
        }
        val level = when (project.seniorityLevel) {
            SeniorityLevel.AUTO -> "自动判断，但不得据此改写职级"
            SeniorityLevel.ENTRY -> "应届或初级岗位"
            SeniorityLevel.JUNIOR -> "初级"
            SeniorityLevel.MID -> "中级"
            SeniorityLevel.SENIOR -> "资深"
            SeniorityLevel.LEAD -> "负责人或技术骨干"
            SeniorityLevel.MANAGER -> "管理岗"
        }
        val ledger = facts.map { "[${it.id}] (${it.kind}) ${it.quote}" }.joinToString("\n")
        return """
$modeContext
目标职级：$level

原始简历：
${project.resumeText.take(MAX_RESUME_CHARS)}

客户补充确认的信息：
${project.supplements.ifBlank { "暂无补充" }.take(MAX_SUPPLEMENT_CHARS)}

事实账本：
${ledger.ifBlank { "事实账本为空。不得自行补充事实。" }}
""".trimIndent()
    }

    private suspend fun complete(
        kind: RequestKind,
        systemPrompt: String,
        userContent: Any
    ): String {
        val config = settings.readAiSettings()
        val provider = config.providers.firstOrNull {
            it.id == if (kind == RequestKind.TEXT) {
                config.activeTextProvider
            } else {
                config.activeVisionProvider
            }
        } ?: error("所选 AI 服务不存在")
        val model = if (kind == RequestKind.TEXT) provider.textModel else provider.visionModel
        return completeWithProvider(provider, model, kind, systemPrompt, userContent)
    }

    private suspend fun completeWithProvider(
        provider: AiProviderSettings,
        model: String,
        kind: RequestKind,
        systemPrompt: String,
        userContent: Any
    ): String {
        validateProvider(provider, model, kind)
        val apiKey = settings.readApiKey(provider.id)?.trim().orEmpty()
        require(apiKey.isNotBlank()) { "${provider.label} 尚未保存 API Key，请先在设置中填写" }

        val payload = JSONObject()
            .put("model", model)
            .put("temperature", 0.05)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userContent))
            )
        val request = Request.Builder()
            .url(provider.endpoint.trim())
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val body = executeWithRetry(request, provider.label)
        return parseContent(body).let(FactGuard::sanitize)
    }

    private fun validateProvider(provider: AiProviderSettings, model: String, kind: RequestKind) {
        require(provider.endpoint.startsWith("https://")) {
            "${provider.label} 的接口地址无效，必须使用 HTTPS"
        }
        require(model.isNotBlank()) {
            if (kind == RequestKind.VISION) {
                "${provider.label} 没有配置视觉模型，请选择支持 OCR 的服务"
            } else {
                "${provider.label} 没有配置文本模型"
            }
        }
    }

    private suspend fun executeWithRetry(request: Request, providerLabel: String): String {
        var retries = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val (status, body) = executeOnce(request)
            if (status in 200..299) return body
            if (status in setOf(429, 500, 502, 503) && retries < 2) {
                retries++
                delay(1500L * retries)
                continue
            }
            val detail = runCatching {
                val json = JSONObject(body)
                json.optString("message").ifBlank {
                    json.optJSONObject("error")?.optString("message").orEmpty()
                }
            }.getOrDefault("")
            error(
                "$providerLabel 接口错误（$status）" +
                    detail.take(180).takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            )
        }
    }

    private suspend fun executeOnce(request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { continuation ->
            val call = HTTP_CLIENT.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("无法连接 AI 服务，请检查手机网络", error)
                        )
                    }
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    try {
                        response.use { continuation.resume(it.code to it.body?.string().orEmpty()) }
                    } catch (error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }

    private fun parseContent(body: String): String {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices")
            ?: error("AI 返回缺少 choices")
        val content = choices.optJSONObject(0)
            ?.optJSONObject("message")
            ?.opt("content")
            ?: error("AI 返回缺少正文")
        val text = when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    append(content.optJSONObject(index)?.optString("text").orEmpty())
                }
            }
            else -> content.toString()
        }.trim()
        require(text.isNotBlank()) { "AI 返回了空内容，请重试或更换模型" }
        return text
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONArray?.toTraceabilityList(): List<TraceabilityEntry> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let { add(ProjectJson.decodeTraceability(it)) }
            }
        }
    }

    private enum class RequestKind {
        TEXT,
        VISION
    }

    companion object {
        private const val MAX_RESUME_CHARS = 40_000
        private const val MAX_JD_CHARS = 20_000
        private const val MAX_SUPPLEMENT_CHARS = 12_000
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val HTTP_CLIENT = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build()

        private val ANALYSIS_BASE_PROMPT = """
你是一名严谨的简历顾问。你的任务是找出简历中真实存在的问题，而不是讨好用户或强行制造岗位匹配。

必须遵守：
1. 只能基于原简历、给定目标信息和客户补充判断，不得编造公司、学历、项目、技术、业绩或数据。
2. 每条问题尽量引用原简历中的短句作为 evidence；原文没有证据时明确写“未体现”。
3. 检查成果证据、表达质量、结构可读性、时间线、职级一致性和事实可信度。
4. 问题要具体可执行，不能只写“优化表达”“突出亮点”。
5. missingQuestions 最多 6 个，只问客户本人通常能回答、且会明显影响简历表达的事实。每条只问一件事，用日常语言，不使用“量化成果、数据归因、职责边界、指标颗粒度”等专业术语。每条不要自带数字编号，程序会统一编号。
6. clientQuestionMessage 是可直接发给候选人的消息，固定采用编号形式 1. 2. 3.，每条一句。先说明已经看过简历，再问具体项目或经历。不要一次追问数字、职责、结果和背景；客户可能不清楚的细节可以用“大概什么量级”“记不清写不确定也可以”来问，不要制造答题压力。
7. 不得建议添加原简历没有依据的数字、职级、团队规模、项目归属或成果。
8. 输出不使用 emoji 和感叹号。
9. 不要把出生日期、年龄、性别、婚育、民族、身份证号、邮箱、手机号、住址和照片列为需要客户核实的内容。它们是联系方式或身份信息，除非同一份材料内存在明显矛盾，否则不进入 missingQuestions、clientQuestionMessage 或 riskWarnings。
""".trimIndent()

        private val ANALYSIS_TARGETED_INSTRUCTION = """
当前模式：特定岗位。
按照给定 JD 判断匹配度，缺少 JD 明确要求的能力时只能指出差距，不得建议虚构。评分名称使用岗位匹配、成果证据、经历相关性、结构与可读性、表达与专业度。jobKeywords 只提取给定 JD 中明确出现的词。
""".trimIndent()

        private val ANALYSIS_ROLE_ONLY_INSTRUCTION = """
当前模式：只有目标岗位方向，没有具体 JD。
可以判断简历内容是否支持该岗位方向，但不得猜测某家公司的招聘要求，不得生成虚构的 JD 关键词。评分名称使用岗位方向清晰度、成果证据、经历相关性、结构与可读性、表达与专业度。jobKeywords 只列出原简历已经出现的相关技能，并说明这是已有能力而非 JD 要求。
""".trimIndent()

        private val ANALYSIS_GENERAL_INSTRUCTION = """
当前模式：通用简历优化，没有目标岗位和 JD。
禁止进行岗位匹配或 JD 关键词覆盖判断。重点检查事实证据、成果表达、职责清晰度、时间线、重复空话、结构和可读性。评分名称使用成果证据、职责清晰度、结构与可读性、表达质量、时间线一致性。jobKeywords 必须返回空数组。
""".trimIndent()

        private val ANALYSIS_JSON_SCHEMA = """
只返回一个 JSON 对象，不要 Markdown。结构：
{
  "overallScore": 0到100的整数,
  "fitSummary": "3到5句诊断结论",
  "dimensions": [{"name":"当前模式对应维度","score":0到100,"detail":"判断依据"}],
  "strengths": ["有原文依据的优势"],
  "issues": [
    {"severity":"CRITICAL或WARNING或INFO","title":"问题标题","evidence":"原文短句或未体现","advice":"具体怎么改，以及需要补充什么事实"}
  ],
  "missingQuestions": ["3到6个通俗、独立、客户通常能回答的问题"],
  "jobKeywords": ["仅按当前模式规则返回"],
  "clientQuestionMessage": "可直接发给客户的真人沟通消息",
  "riskWarnings": ["时间线、职级、真实性、ATS可读性等风险"]
}
""".trimIndent()

        private val FACT_EXTRACTION_PROMPT = """
你是一个事实提取器。只从原简历和客户补充中逐条提取可核验事实，为后续简历改写建立事实账本。

只返回 JSON：
{"facts":[{"id":"F1","quote":"原句或最小完整事实片段","kind":"经历/职责/数字/成果/技能/教育/时间/其他","verified":false}]}

规则：
1. quote 必须逐字来自输入材料，不得改写、合并或补全。
2. 数字、百分比、金额、用户量、职级、团队规模、项目归属和成果要单独提取。
3. 不提取空话和主观评价。
4. 最多 120 条。不要输出 Markdown、emoji 或感叹号。
""".trimIndent()

        private val REWRITE_SYSTEM_PROMPT = """
你是一名极度保守的简历事实编辑。客户事实保真优先于岗位匹配观感。

唯一允许的修改：
1. 调整语序和组织结构。
2. 删除空话、重复和无关内容。
3. 把口语或松散表达改为正式、简洁的中文。
4. 使用原简历中已经明确存在的信息来突出与给定目标信息的联系。没有目标信息时只提升清晰度和可读性。
5. 可以重新分章节和调整项目顺序，但不得新增职业定位、自我评价、能力总结、职责条目或成果句。原文没有对应信息时保持缺失。

绝对禁止：
1. 编造或推测任何数字、百分比、金额、用户量、职级、团队规模、项目归属、项目周期和成果。
2. 把“参与”“协助”“配合”升级为“主导”“负责”“牵头”“独立完成”或其他更强的职责表述。
3. 为匹配目标岗位或 JD 添加原简历和客户补充中不存在的技能、工具、证书、行业经验或关键词。
4. 把团队成果写成个人成果，把公司成果写成个人成果，把协助事项写成独立事项。
5. 使用 emoji 或感叹号。
6. 生成没有原文依据的“动作加结果”句式。只有原简历或客户补充明确给出结果时，才能写结果。

逐句自检后才允许输出。每个事实必须能在原简历或客户补充中找到依据；找不到就删除，或写成“[待确认：具体内容]”，不得猜测。
每个 section 都要在 sourceEvidence 中给出对应的原简历短句；纯章节标题可以留空。
verificationChecklist 必须列出所有仍可能被误读、被夸大或需要客户确认的事实。
traceability 必须为每一句包含数字、职责边界或成果的修订表述给出来源。

只返回 JSON，不要 Markdown：
{
  "sections":[
    {
      "title":"章节名",
      "content":"只包含可交付的简历正文",
      "changeNote":"只说明调整语序、删空话或正式化表达",
      "benefit":"这次调整对清晰度、真实度或可读性的具体收益，不得夸大",
      "originalContent":"该章节对应的原简历完整原文；必须逐字摘自输入，不得改写",
      "sourceEvidence":"对应原文短句；没有则留空"
    }
  ],
  "verificationChecklist":["需要客户逐条核实的事实或表述"],
  "traceability":[
    {
      "fact":"修订稿中的事实",
      "rewrittenText":"修订稿具体表述",
      "sourceEvidence":"原简历或客户补充中的对应原文",
      "status":"有原文依据或待核实"
    }
  ]
}

输出前再次逐句检查：每个数字、职级、团队规模、项目归属、职责边界和成果是否有原文依据。没有依据的内容必须删除或标记待确认。
不要自行生成“需客户核实清单”和“数据溯源表”章节，交付程序会在末尾统一生成。
""".trimIndent()

        private val SECOND_REVIEW_PROMPT = """
你是独立的简历事实审核员。你不是改写者，只检查另一模型生成的修订稿。

逐项检查：
1. 数字、百分比、金额、用户量、职级、团队规模、项目归属和成果是否有输入原文依据。
2. 是否把参与、协助、配合升级为主导、负责、牵头、独立完成。
3. 是否把团队成果写成个人成果。
4. 是否为了岗位匹配添加了原文没有的技能、工具或关键词。
5. 是否存在无法在原文找到的“动作加结果”。
6. 是否存在时间线矛盾、职级矛盾和隐私残留。

只返回 JSON：
{"findings":["具体问题，指出修订表述并要求客户核实"]}

如果未发现问题，findings 返回空数组。不能替改写稿补造证据，不使用 emoji 或感叹号。
""".trimIndent()

        private val OCR_SYSTEM_PROMPT = """
你是一个精确的文档转录器。把图片中的简历文字按阅读顺序完整转录。
保持姓名、公司、日期、数字、英文缩写和技术名词准确；不确定的字用“□”标记，不要猜测。
保留段落与项目符号，不总结、不改写、不补充。只输出转录文字。
""".trimIndent()
    }
}
