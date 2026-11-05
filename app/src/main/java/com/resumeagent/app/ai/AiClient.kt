package com.resumeagent.app.ai

import android.util.Base64
import com.resumeagent.app.data.ProjectJson
import com.resumeagent.app.data.SecureSettings
import com.resumeagent.app.domain.AiProviderSettings
import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.FactAuditResult
import com.resumeagent.app.domain.FactGuard
import com.resumeagent.app.domain.ResumeAnalysis
import com.resumeagent.app.domain.ResumeProject
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
        val response = complete(
            kind = RequestKind.TEXT,
            systemPrompt = ANALYSIS_SYSTEM_PROMPT,
            userText = """
目标岗位：${project.targetRole}
目标公司：${project.company.ifBlank { "未填写" }}
目标行业：${project.industry.ifBlank { "未填写" }}

原始简历：
${project.resumeText.take(MAX_RESUME_CHARS)}

岗位 JD：
${project.jobDescription.take(MAX_JD_CHARS)}
""".trimIndent()
        )
        val json = runCatching { AiResultParser.objectFrom(response) }
            .getOrElse { error("AI 返回的诊断结果无法解析，请重试或更换文本模型") }
        val analysis = ProjectJson.decodeAnalysis(json)
        require(analysis.fitSummary.isNotBlank()) { "AI 没有返回适配度结论" }
        require(analysis.dimensions.isNotEmpty()) { "AI 没有返回诊断维度" }
        return analysis
    }

    suspend fun rewrite(project: ResumeProject): FactAuditResult {
        val analysis = project.analysis ?: error("请先完成简历诊断")
        val issues = analysis.issues.joinToString("\n") {
            "- [${it.severity}] ${it.title}：${it.advice}"
        }
        val response = complete(
            kind = RequestKind.TEXT,
            systemPrompt = REWRITE_SYSTEM_PROMPT,
            userText = """
目标岗位：${project.targetRole}
目标公司：${project.company.ifBlank { "未填写" }}
目标行业：${project.industry.ifBlank { "未填写" }}

原始简历：
${project.resumeText.take(MAX_RESUME_CHARS)}

岗位 JD：
${project.jobDescription.take(MAX_JD_CHARS)}

诊断发现：
$issues

用户补充确认的信息：
${project.supplements.ifBlank { "暂无补充" }.take(MAX_SUPPLEMENT_CHARS)}
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
                    sourceEvidence = FactGuard.sanitize(item.optString("sourceEvidence"))
                ))
            }
        }
        require(sections.isNotEmpty()) { "AI 没有生成可用的简历段落" }

        val checklist = json.optJSONArray("verificationChecklist").toStringList()
        val traceability = json.optJSONArray("traceability").toTraceabilityList()
        return FactGuard.audit(project, sections, checklist, traceability)
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

        private val ANALYSIS_SYSTEM_PROMPT = """
你是一名严谨的简历顾问和招聘分析员。你的任务不是讨好用户，而是找出简历为什么无法通过目标岗位筛选。

必须遵守：
1. 只能基于原简历、岗位 JD 和用户补充信息判断，不得编造公司、学历、项目、技术、业绩或数据。
2. 每条问题尽量引用原简历中的短句作为 evidence；原文没有证据时明确写“未体现”。
3. 同时检查岗位匹配、成果证据、表达质量、结构可读性、时间线和职级一致性。
4. 问题要具体可执行，不能只写“优化表达”“突出亮点”。
5. missingQuestions 只问那些补上后会显著提升简历的事实，不能要求敏感隐私。措辞要像接单的人当面沟通，具体到项目或经历，不要写“请提供更多信息”“请量化成果”这类机器化句子。
6. clientQuestionMessage 是一段可以直接复制发给候选人的消息。先简短说明已经看过简历和 JD，再结合具体经历问 3 到 6 个最值得确认的问题。语气自然、坦诚、尊重，不奉承，不制造焦虑，不用“打造核心竞争力”“全面提升”等套话。最后说明不确定的事项可以直接写不确定。
   参考语气：您好，我看过您的简历和岗位要求。想先和您确认两处经历，避免改写时把您的工作范围和结果写偏。您提到的某项目，当时大概服务多少用户，您具体负责哪一块。如果记不清具体数字，直接写不确定就可以。
7. 适配度评分用于内部参考，不代表录用概率。
8. 不得建议通过添加原简历没有依据的数字、职级、团队规模、项目归属或成果来匹配 JD。
9. 输出不使用 emoji 和感叹号。

只返回一个 JSON 对象，不要 Markdown。结构：
{
  "overallScore": 0到100的整数,
  "fitSummary": "3到5句结论，说明当前最核心的匹配点和硬伤",
  "dimensions": [
    {"name":"岗位匹配","score":0到100,"detail":"判断依据"},
    {"name":"成果证据","score":0到100,"detail":"判断依据"},
    {"name":"经历相关性","score":0到100,"detail":"判断依据"},
    {"name":"结构与可读性","score":0到100,"detail":"判断依据"},
    {"name":"表达与专业度","score":0到100,"detail":"判断依据"}
  ],
  "strengths": ["有原文依据的优势"],
  "issues": [
    {"severity":"CRITICAL或WARNING或INFO","title":"问题标题","evidence":"原文短句或未体现","advice":"具体怎么改，以及需要补充什么事实"}
  ],
  "missingQuestions": ["最多8个，按具体项目或经历提问，便于用户直接回答"],
  "jobKeywords": ["JD中的重要技能、工具、职责或成果关键词"],
  "clientQuestionMessage": "可直接发给候选人的自然沟通消息，结合具体经历提问，不用套话",
  "riskWarnings": ["时间线、职级、真实性、ATS可读性等风险"]
}
""".trimIndent()

        private val REWRITE_SYSTEM_PROMPT = """
你是一名极度保守的简历事实编辑。客户事实保真优先于岗位匹配观感。

唯一允许的修改：
1. 调整语序和组织结构。
2. 删除空话、重复和无关内容。
3. 把口语或松散表达改为正式、简洁的中文。
4. 使用原简历中已经明确存在的信息来突出与 JD 的关系。
5. 可以重新分章节和调整项目顺序，但不得新增职业定位、自我评价、能力总结、职责条目或成果句。原文没有对应信息时保持缺失。

绝对禁止：
1. 编造或推测任何数字、百分比、金额、用户量、职级、团队规模、项目归属、项目周期和成果。
2. 把“参与”“协助”“配合”升级为“主导”“负责”“牵头”“独立完成”或其他更强的职责表述。
3. 为匹配 JD 添加原简历和客户补充中不存在的技能、工具、证书、行业经验或关键词。
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

        private const val OCR_SYSTEM_PROMPT = """
你是一个精确的文档转录器。把图片中的简历文字按阅读顺序完整转录。
保持姓名、公司、日期、数字、英文缩写和技术名词准确；不确定的字用“□”标记，不要猜测。
保留段落与项目符号，不总结、不改写、不补充。只输出转录文字。
""".trimIndent()
    }
}
