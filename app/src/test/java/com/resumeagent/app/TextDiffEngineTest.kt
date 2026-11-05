package com.resumeagent.app

import com.resumeagent.app.domain.DiffKind
import com.resumeagent.app.domain.TextDiffEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class TextDiffEngineTest {
    @Test
    fun reportsChangedSentence() {
        val diff = TextDiffEngine.compare(
            "参与需求分析。负责会议记录。",
            "参与需求分析。整理会议记录。"
        )

        assertEquals(DiffKind.SAME, diff[0].kind)
        assertEquals(DiffKind.CHANGED, diff[1].kind)
    }
}
