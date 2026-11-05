package com.resumeagent.app.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.resumeagent.app.data.ProjectStore
import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.ResumeProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportBundle(
    val docx: File,
    val pdf: File,
    val report: File
)

class DocumentExporter(
    private val context: Context,
    private val store: ProjectStore
) {
    suspend fun export(project: ResumeProject): ExportBundle = withContext(Dispatchers.IO) {
        val sections = project.acceptedSections
        require(sections.isNotEmpty()) { "没有已接受的简历段落" }
        val deliverableSections = sections + createFactAppendix(project)
        val baseName = sanitizeFileName(
            buildString {
                append(project.targetRole.ifBlank { "简历" })
                if (project.company.isNotBlank()) append("_${project.company}")
                append("_修订版")
            }
        )
        val output = File(store.exportDirectory(), "${System.currentTimeMillis()}_${project.id}")
            .apply { mkdirs() }
        val docx = File(output, "$baseName.docx").apply { writeBytes(createDocx(project, deliverableSections)) }
        val pdf = File(output, "$baseName.pdf").apply {
            outputStream().use { createPdf(project, deliverableSections, it) }
        }
        val report = File(output, "${baseName}_修改报告.txt").apply {
            writeText(createReport(project, sections))
        }
        ExportBundle(docx, pdf, report)
    }

    private fun createDocx(project: ResumeProject, sections: List<DraftSection>): ByteArray {
        val documentXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
            sections.forEachIndexed { index, section ->
                append(docxParagraph(section.title, heading = true, first = index == 0))
                section.content.lines().forEach { line ->
                    if (line.isNotBlank()) append(docxParagraph(line))
                }
            }
            append("<w:sectPr>")
            append("""<w:pgSz w:w="11906" w:h="16838"/>""")
            append("""<w:pgMar w:top="850" w:right="900" w:bottom="850" w:left="900" w:header="400" w:footer="400" w:gutter="0"/>""")
            append("</w:sectPr></w:body></w:document>")
        }
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
              <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
            </Types>
        """.trimIndent()
        val relationships = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()
        val styles = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:docDefaults><w:rPrDefault><w:rPr>
                <w:rFonts w:ascii="Aptos" w:hAnsi="Aptos" w:eastAsia="Microsoft YaHei"/>
                <w:sz w:val="21"/><w:szCs w:val="21"/>
              </w:rPr></w:rPrDefault></w:docDefaults>
            </w:styles>
        """.trimIndent()

        return java.io.ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write(contentTypes.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write(relationships.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(documentXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("word/styles.xml"))
                zip.write(styles.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            bytes.toByteArray()
        }.also {
            // Keep the exported document self-describing when opened outside the app.
            require(project.targetRole.isNotBlank())
        }
    }

    private fun docxParagraph(text: String, heading: Boolean = false, first: Boolean = false): String {
        val escaped = xmlEscape(sanitize(text))
        return buildString {
            append("<w:p><w:pPr>")
            if (heading) {
                append("""<w:spacing w:before="${if (first) 0 else 220}" w:after="100"/>""")
                append("""<w:keepNext/><w:outlineLvl w:val="0"/>""")
            } else {
                append("""<w:spacing w:after="70" w:line="330" w:lineRule="auto"/>""")
            }
            append("</w:pPr><w:r><w:rPr>")
            if (heading) append("<w:b/><w:color w:val=\"1F6B5A\"/><w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/>")
            else append("<w:sz w:val=\"21\"/><w:szCs w:val=\"21\"/>")
            append("</w:rPr><w:t xml:space=\"preserve\">$escaped</w:t></w:r></w:p>")
        }
    }

    private fun createPdf(
        project: ResumeProject,
        sections: List<DraftSection>,
        output: java.io.OutputStream
    ) {
        val pdf = PdfDocument()
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF20242B.toInt()
            textSize = 10.5f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val headingPaint = Paint(bodyPaint).apply {
            color = 0xFF1F6B5A.toInt()
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var canvas = page.canvas
        val margin = 42f
        val contentWidth = 595f - margin * 2
        var y = 52f

        fun nextPage() {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            canvas = page.canvas
            y = 52f
        }

        fun drawWrapped(text: String, paint: Paint, indent: Float = 0f, spacingAfter: Float = 5f) {
            val available = contentWidth - indent
            val lines = wrapText(sanitize(text).ifBlank { " " }, paint, available)
            lines.forEach { line ->
                val lineHeight = paint.textSize * 1.55f
                if (y + lineHeight > 800f) nextPage()
                canvas.drawText(line, margin + indent, y, paint)
                y += lineHeight
            }
            y += spacingAfter
        }

        sections.forEachIndexed { index, section ->
            if (y > 740f && index > 0) nextPage()
            drawWrapped(section.title, headingPaint, spacingAfter = 7f)
            section.content.lines().forEach { line ->
                if (line.isBlank()) {
                    y += 4f
                } else {
                    val trimmed = line.trim()
                    val indent = if (trimmed.startsWith("•") || trimmed.startsWith("-")) 10f else 0f
                    drawWrapped(trimmed, bodyPaint, indent = indent)
                }
            }
            y += 3f
        }

        pdf.finishPage(page)
        pdf.writeTo(output)
        pdf.close()
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        text.forEach { char ->
            val candidate = current.toString() + char
            if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += current.toString()
                current.clear()
            }
            current.append(char)
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun createReport(project: ResumeProject, sections: List<DraftSection>): String =
        sanitize(buildString {
            appendLine("简历修改报告")
            appendLine("目标岗位：${project.targetRole}")
            appendLine("目标公司：${project.company.ifBlank { "未填写" }}")
            appendLine("目标行业：${project.industry.ifBlank { "未填写" }}")
            appendLine()
            project.analysis?.let { analysis ->
                appendLine("适配度综合评分：${analysis.overallScore}/100（内部参考，不代表录用概率）")
                appendLine(analysis.fitSummary)
                appendLine()
                appendLine("分项诊断")
                analysis.dimensions.forEach {
                    appendLine("- ${it.name}：${it.score}/100，${it.detail}")
                }
                appendLine()
                appendLine("主要问题与建议")
                analysis.issues.forEach {
                    appendLine("- [${it.severity}] ${it.title}")
                    appendLine("  原文/证据：${it.evidence}")
                    appendLine("  建议：${it.advice}")
                }
                if (analysis.riskWarnings.isNotEmpty()) {
                    appendLine()
                    appendLine("风险提醒")
                    analysis.riskWarnings.forEach { appendLine("- $it") }
                }
            }
            appendLine()
            appendLine("本次改写章节")
            sections.forEach {
                appendLine("- ${it.title}：${it.changeNote.ifBlank { "已结合目标岗位重新组织" }}")
            }
            appendLine()
            appendLine("需客户核实清单")
            if (project.verificationChecklist.isEmpty()) {
                appendLine("- 未发现新增数字、职责升级或无依据关键词，仍请客户逐项核对事实。")
            } else {
                project.verificationChecklist.forEach { appendLine("- ${sanitize(it)}") }
            }
            appendLine()
            appendLine("数据溯源表")
            if (project.traceability.isEmpty()) {
                appendLine("- AI 未返回可逐条核对的数据，请人工复核数字、职责边界和成果。")
            } else {
                project.traceability.forEachIndexed { index, entry ->
                    appendLine("${index + 1}. 事实：${sanitize(entry.fact)}")
                    appendLine("   修订表述：${sanitize(entry.rewrittenText)}")
                    appendLine("   原文依据：${sanitize(entry.sourceEvidence).ifBlank { "未提供" }}")
                    appendLine("   状态：${sanitize(entry.status).ifBlank { "待核实" }}")
                }
            }
            appendLine()
            appendLine("注意：所有新增事实必须经候选人确认，导出前请检查“待补充”占位符。")
        })

    private fun createFactAppendix(project: ResumeProject): List<DraftSection> {
        val checklist = if (project.verificationChecklist.isEmpty()) {
            "未发现新增数字、职责升级或无依据关键词，仍请客户逐项核对真实经历。"
        } else {
            project.verificationChecklist.mapIndexed { index, item ->
                "${index + 1}. ${sanitize(item)}"
            }.joinToString("\n")
        }
        val traceability = if (project.traceability.isEmpty()) {
            "AI 未返回可逐条核对的数据，请人工复核全部数字、职责边界和成果。"
        } else {
            project.traceability.mapIndexed { index, entry ->
                buildString {
                    append("${index + 1}. 事实：${sanitize(entry.fact)}")
                    append("\n修订表述：${sanitize(entry.rewrittenText)}")
                    append("\n原文依据：${sanitize(entry.sourceEvidence).ifBlank { "未提供" }}")
                    append("\n状态：${sanitize(entry.status).ifBlank { "待核实" }}")
                }
            }.joinToString("\n\n")
        }
        return listOf(
            DraftSection(title = "需客户核实清单", content = checklist),
            DraftSection(title = "数据溯源表", content = traceability)
        )
    }

    private fun xmlEscape(text: String): String = buildString {
        text.forEach {
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

    private fun sanitizeFileName(name: String): String =
        sanitize(name)
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_")
            .take(80)
            .ifBlank { "简历_修订版" }

    private fun sanitize(value: String): String =
        value.replace(Regex("""[\p{So}\p{Sk}\uFE0F\u200D]+"""), "")
            .replace(Regex("[!！]+"), "。")
            .replace(Regex("[。]{2,}"), "。")
            .trim()
}
