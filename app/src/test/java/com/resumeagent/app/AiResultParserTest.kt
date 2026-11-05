package com.resumeagent.app

import com.resumeagent.app.ai.AiResultParser
import org.junit.Assert.assertEquals
import org.junit.Test

class AiResultParserTest {
    @Test
    fun extractsJsonFromMarkdownFence() {
        val parsed = AiResultParser.objectFrom(
            """
            ```json
            {"sections":[{"title":"工作经历","content":"仅使用原文事实"}]}
            ```
            """.trimIndent()
        )

        assertEquals(1, parsed.getJSONArray("sections").length())
    }
}
