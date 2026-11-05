package com.resumeagent.app

import com.resumeagent.app.domain.RuleChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleCheckerTest {
    @Test
    fun detectsMissingContactAndPlaceholder() {
        val checks = RuleChecker.check(
            "2019-2024 工作经历。负责项目。负责项目。[待补充：用户量]"
        )

        assertFalse(checks.first { it.name == "联系方式" }.passed)
        assertFalse(checks.first { it.name == "重复句" }.passed)
        assertFalse(checks.first { it.name == "占位符" }.passed)
    }

    @Test
    fun acceptsBasicCompleteResume() {
        val checks = RuleChecker.check(
            """
            张三
            13800138000
            2019.07-2021.08 某公司 产品经理
            2021.09-2024.06 某公司 高级产品经理
            参与需求分析并整理业务流程，原文没有进一步结果数据。
            """.trimIndent()
        )

        assertTrue(checks.first { it.name == "联系方式" }.passed)
        assertTrue(checks.first { it.name == "时间信息" }.passed)
    }
}
