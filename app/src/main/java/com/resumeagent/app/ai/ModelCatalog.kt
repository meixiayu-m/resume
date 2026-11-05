package com.resumeagent.app.ai

import com.resumeagent.app.domain.AiModelInfo
import com.resumeagent.app.domain.AiProviderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ModelCatalog {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun fetch(provider: AiProviderSettings, apiKey: String): List<AiModelInfo> =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "请先保存 ${provider.label} 的 API Key" }
            val endpoint = provider.modelsEndpoint.ifBlank {
                provider.endpoint
                    .replace("/chat/completions", "/models")
                    .trimEnd('/')
            }
            require(endpoint.startsWith("https://")) { "${provider.label} 的模型列表地址无效" }
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        val json = JSONObject(body)
                        json.optString("message").ifBlank {
                            json.optJSONObject("error")?.optString("message").orEmpty()
                        }
                    }.getOrDefault("")
                    error(
                        "${provider.label} 模型列表请求失败（${response.code}）" +
                            detail.take(180).takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
                    )
                }
                parse(body)
            }
        }

    private fun parse(body: String): List<AiModelInfo> {
        val root = JSONObject(body)
        val data = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.optString("id")
                    .ifBlank { item.optString("name") }
                    .trim()
                if (id.isBlank()) continue
                add(
                    AiModelInfo(
                        id = id,
                        ownedBy = item.optString("owned_by"),
                        contextLength = firstLong(
                            item,
                            "context_length",
                            "max_context_length",
                            "context_window",
                            "max_tokens"
                        ),
                        description = item.optString("description")
                    )
                )
            }
        }.distinctBy { it.id }.sortedBy { it.id }
    }

    private fun firstLong(item: JSONObject, vararg names: String): Long? = names
        .asSequence()
        .mapNotNull { name ->
            if (!item.has(name) || item.isNull(name)) null
            else item.optLong(name, Long.MIN_VALUE).takeIf { it > 0 }
        }
        .firstOrNull()
}
