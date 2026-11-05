package com.resumeagent.app.domain

enum class DiffKind {
    SAME,
    CHANGED,
    ADDED,
    REMOVED
}

data class DiffLine(
    val original: String,
    val revised: String,
    val kind: DiffKind
)

object TextDiffEngine {
    private val sentenceBreak = Regex("""(?<=[。；;！!？?\n])""")

    fun compare(original: String, revised: String): List<DiffLine> {
        val originalLines = split(original)
        val revisedLines = split(revised)
        return (0 until maxOf(originalLines.size, revisedLines.size)).map { index ->
            val before = originalLines.getOrNull(index).orEmpty()
            val after = revisedLines.getOrNull(index).orEmpty()
            DiffLine(
                original = before,
                revised = after,
                kind = when {
                    before.isBlank() -> DiffKind.ADDED
                    after.isBlank() -> DiffKind.REMOVED
                    before == after -> DiffKind.SAME
                    else -> DiffKind.CHANGED
                }
            )
        }
    }

    private fun split(value: String): List<String> = value
        .split(sentenceBreak)
        .map(String::trim)
        .filter(String::isNotBlank)
}
