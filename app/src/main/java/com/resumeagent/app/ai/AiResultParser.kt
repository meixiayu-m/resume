package com.resumeagent.app.ai

import org.json.JSONArray
import org.json.JSONObject

object AiResultParser {
    fun objectFrom(text: String): JSONObject = JSONObject(cleanJson(text))

    fun arrayField(text: String, field: String): JSONArray {
        val root = objectFrom(text)
        return root.optJSONArray(field) ?: error("AI 返回缺少 $field 字段")
    }

    fun cleanJson(text: String): String {
        val trimmed = text.trim()
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 返回的不是有效 JSON" }
        return trimmed.substring(start, end + 1)
    }
}
