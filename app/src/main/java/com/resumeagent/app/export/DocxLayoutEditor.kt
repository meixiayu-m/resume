package com.resumeagent.app.export

import com.resumeagent.app.domain.ParagraphAction
import com.resumeagent.app.domain.ParagraphRevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class DocxParagraph(
    val index: Int,
    val text: String,
    val hasObject: Boolean
)

object DocxLayoutEditor {
    private val paragraphPattern = Regex(
        """<w:p(?:\s[^>]*)?>.*?</w:p>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val textPattern = Regex(
        """(<w:t\b[^>]*>)(.*?)(</w:t>)""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    suspend fun extractParagraphs(source: File): List<DocxParagraph> = withContext(Dispatchers.IO) {
        val xml = readDocumentXml(source)
        var index = 0
        paragraphPattern.findAll(xml).mapNotNull { match ->
            val paragraph = match.value
            val text = textPattern.findAll(paragraph)
                .joinToString("") { xmlUnescape(it.groupValues[2]) }
                .trim()
            if (text.isBlank()) return@mapNotNull null
            index += 1
            DocxParagraph(
                index = index,
                text = text,
                hasObject = Regex("""<w:(?:drawing|pict)\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(paragraph)
            )
        }.toList()
    }

    suspend fun apply(
        source: File,
        revisions: List<ParagraphRevision>,
        target: File
    ) = withContext(Dispatchers.IO) {
        val changes = revisions.associateBy { it.index }
        var textParagraphIndex = 0
        val sourceXml = readDocumentXml(source)
        val outputXml = paragraphPattern.replace(sourceXml) { match ->
            val paragraph = match.value
            val text = textPattern.findAll(paragraph)
                .joinToString("") { xmlUnescape(it.groupValues[2]) }
                .trim()
            if (text.isBlank()) {
                paragraph
            } else {
                textParagraphIndex += 1
                val revision = changes[textParagraphIndex]
                when (revision?.action) {
                    ParagraphAction.DELETE -> ""
                    ParagraphAction.CHANGE -> replaceParagraphText(paragraph, revision.revised)
                    ParagraphAction.KEEP, null -> paragraph
                }
            }
        }
        ZipFile(source).use { input ->
            ByteArrayOutputStream().use { bytes ->
                ZipOutputStream(bytes).use { output ->
                    input.entries().asSequence().forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name))
                        val content = input.getInputStream(entry).use { it.readBytes() }
                        output.write(
                            if (entry.name == "word/document.xml") {
                                outputXml.toByteArray(Charsets.UTF_8)
                            } else {
                                content
                            }
                        )
                        output.closeEntry()
                    }
                }
                target.writeBytes(bytes.toByteArray())
            }
        }
    }

    private fun replaceParagraphText(paragraph: String, newText: String): String {
        var replaced = false
        return textPattern.replace(paragraph) { match ->
            val value = if (replaced) "" else xmlEscape(newText)
            replaced = true
            match.groupValues[1] + value + match.groupValues[3]
        }
    }

    private fun readDocumentXml(source: File): String = ZipFile(source).use { zip ->
        val entry = zip.getEntry("word/document.xml") ?: error("DOCX 缺少 word/document.xml")
        zip.getInputStream(entry).use { String(it.readBytes(), Charsets.UTF_8) }
    }

    private fun xmlEscape(value: String): String = buildString {
        value.forEach {
            when (it) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(it)
            }
        }
    }

    private fun xmlUnescape(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
