package com.resumeagent.app.export

import com.resumeagent.app.domain.ParagraphAction
import com.resumeagent.app.domain.ParagraphRevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
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
        target: File,
        orderedSections: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val changes = revisions.associateBy { it.index }
        var textParagraphIndex = 0
        val sourceXml = readDocumentXml(source)
        var outputXml = paragraphPattern.replace(sourceXml) { match ->
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
        if (orderedSections.isNotEmpty()) {
            outputXml = reorderSections(outputXml, orderedSections)
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

    private fun reorderSections(xml: String, orderedSections: List<String>): String {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val body = document.getElementsByTagNameNS("*", "body").item(0)
            ?: error("无法识别 DOCX 正文")
        val nodes = (0 until body.childNodes.length).map { body.childNodes.item(it) }
        val paragraphs = nodes.mapIndexedNotNull { index, node ->
            if (node.localName != "p") return@mapIndexedNotNull null
            val text = normalizeHeading(node.textContent)
            if (text.isBlank()) return@mapIndexedNotNull null
            index to text
        }
        val starts = orderedSections.map { title ->
            val normalizedTitle = normalizeHeading(title)
            val exact = paragraphs.filter { (_, text) -> text == normalizedTitle }
            val candidates = exact.ifEmpty { paragraphs.filter { (_, text) ->
                text == normalizedTitle ||
                    (normalizedTitle.length >= 2 && text.contains(normalizedTitle)) ||
                    (text.length >= 2 && normalizedTitle.contains(text))
            } }
            require(candidates.size == 1) {
                "原 DOCX 中无法唯一定位“$title”模块标题，已停止生成调整顺序版；原模板修订版仍可使用。"
            }
            candidates.single().first
        }
        require(starts.distinct().size == orderedSections.size) {
            "原 DOCX 中多个模块对应到同一标题，已停止生成调整顺序版；原模板修订版仍可使用。"
        }
        val sortedStarts = starts.sorted()
        val firstStart = sortedStarts.firstOrNull() ?: return xml
        val sectionPropertiesIndex = nodes.indexOfLast {
            it.nodeType == org.w3c.dom.Node.ELEMENT_NODE && it.localName == "sectPr"
        }.takeIf { it >= 0 } ?: nodes.size
        val prefix = nodes.take(firstStart)
        val currentBlocks = sortedStarts.mapIndexed { index, start ->
            val end = sortedStarts.getOrNull(index + 1) ?: sectionPropertiesIndex
            start until end
        }
        val byStart = sortedStarts.zip(currentBlocks).toMap()
        val orderByStart = starts.withIndex().associate { it.value to it.index }
        val reorderedNodes = buildList {
            addAll(prefix)
            sortedStarts.sortedBy { orderByStart[it] }.forEach { start ->
                byStart.getValue(start).forEach { add(nodes[it]) }
            }
            nodes.drop(sectionPropertiesIndex).forEach(::add)
        }
        while (body.hasChildNodes()) body.removeChild(body.firstChild)
        reorderedNodes.forEach(body::appendChild)
        return ByteArrayOutputStream().use { output ->
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            }.transform(DOMSource(document), StreamResult(output))
            String(output.toByteArray(), Charsets.UTF_8)
        }
    }

    private fun normalizeHeading(value: String): String = value
        .replace(Regex("[\\s:：|｜·•、，。()（）【】\\[\\]{}_-]+"), "")
        .lowercase()

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
