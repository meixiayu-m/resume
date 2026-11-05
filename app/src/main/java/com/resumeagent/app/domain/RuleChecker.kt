package com.resumeagent.app.domain

object RuleChecker {
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val phone = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""")
    private val fillerPhrases = listOf(
        "认真负责",
        "积极主动",
        "沟通能力强",
        "具有团队精神",
        "学习能力强",
        "熟练使用办公软件"
    )

    fun check(resumeText: String): List<RuleCheck> {
        val text = resumeText.trim()
        val lines = text.lines().map(String::trim).filter(String::isNotBlank)
        val duplicates = lines.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        val longLines = lines.filter { it.length > 220 }
        val placeholders = Regex("""待补充|待确认|\[[^\]]*(?:具体|内容)[^\]]*\]""")
            .findAll(text)
            .count()
        val fillerHits = fillerPhrases.filter { text.contains(it) }
        val years = Regex("""(?<!\d)(?:19|20)\d{2}(?:\s*[./-]\s*\d{1,2})?(?!\d)""")
            .findAll(text)
            .map { it.value }
            .toList()

        return listOf(
            RuleCheck(
                name = "联系方式",
                passed = email.containsMatchIn(text) || phone.containsMatchIn(text),
                detail = if (email.containsMatchIn(text) || phone.containsMatchIn(text)) {
                    "检测到手机号或邮箱"
                } else {
                    "没有检测到常见手机号或邮箱，请确认客户是否刻意隐藏"
                }
            ),
            RuleCheck(
                name = "重复句",
                passed = duplicates.isEmpty(),
                detail = if (duplicates.isEmpty()) "未发现完全重复的段落" else "重复 ${duplicates.size} 段，建议合并"
            ),
            RuleCheck(
                name = "段落长度",
                passed = longLines.isEmpty(),
                detail = if (longLines.isEmpty()) "没有超长段落" else "有 ${longLines.size} 段超过 220 字，手机和 ATS 阅读都可能吃力"
            ),
            RuleCheck(
                name = "占位符",
                passed = placeholders == 0,
                detail = if (placeholders == 0) "未发现待确认占位符" else "发现 $placeholders 处待确认占位符，交付前需要处理"
            ),
            RuleCheck(
                name = "空泛表达",
                passed = fillerHits.isEmpty(),
                detail = if (fillerHits.isEmpty()) "未检测到明显空话" else "检测到：${fillerHits.joinToString("、")}"
            ),
            RuleCheck(
                name = "时间信息",
                passed = years.size >= 2,
                detail = if (years.size >= 2) {
                    "检测到 ${years.size} 处年份信息，仍需人工核对连续性和空档"
                } else {
                    "年份信息较少，请确认工作经历是否包含时间范围"
                }
            ),
            RuleCheck(
                name = "篇幅",
                passed = text.length in 300..8_000,
                detail = when {
                    text.length < 300 -> "内容较短，可能缺少关键经历或项目"
                    text.length > 8_000 -> "内容较长，建议按目标岗位删减重复信息"
                    else -> "文字量适合常见 1 至 2 页简历"
                }
            )
        )
    }
}
