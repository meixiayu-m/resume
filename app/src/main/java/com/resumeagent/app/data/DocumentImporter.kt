package com.resumeagent.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Xml
import com.resumeagent.app.ai.AiClient
import com.resumeagent.app.domain.ImportedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class DocumentImporter(
    private val context: Context,
    private val aiClient: AiClient,
    private val store: ProjectStore
) {
    suspend fun import(
        projectId: String,
        uri: Uri,
        fileKind: String = "resume"
    ): ImportedDocument = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "resume"
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val safeExtension = extension.takeIf { it in setOf("docx", "pdf", "txt", "md", "jpg", "jpeg", "png", "webp") }
            ?: "bin"
        val safeKind = fileKind.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val target = File(store.projectDirectory(projectId), "${safeKind}_source.$safeExtension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use(input::copyTo)
        } ?: error("无法读取所选文件")

        when (safeExtension) {
            "docx" -> ImportedDocument(displayName, target.absolutePath, extractDocx(target))
            "txt", "md" -> ImportedDocument(
                displayName = displayName,
                localPath = target.absolutePath,
                text = target.readText().take(MAX_TEXT_CHARS)
            )
            "pdf" -> ImportedDocument(
                displayName = displayName,
                localPath = target.absolutePath,
                text = ocrPdf(target),
                needsOcr = true
            )
            "jpg", "jpeg", "png", "webp" -> ImportedDocument(
                displayName = displayName,
                localPath = target.absolutePath,
                text = ocrImage(target),
                needsOcr = true
            )
            else -> error("暂不支持该文件格式，请使用 DOCX、PDF、TXT 或图片")
        }.also {
            require(it.text.isNotBlank()) { "没有从文件中提取到文字，请尝试其他格式或手动粘贴" }
        }
    }

    private suspend fun ocrPdf(file: File): String {
        val images = mutableListOf<ByteArray>()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val pageCount = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    repeat(pageCount) { index ->
                        renderer.openPage(index).use { page ->
                            val scale = 1500f / maxOf(page.width, page.height)
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            images += bitmap.toJpeg(82)
                            bitmap.recycle()
                        }
                    }
                }
            }
        } catch (error: SecurityException) {
            throw IllegalStateException("系统不允许直接读取这个 PDF，请重新选择文件", error)
        }
        return aiClient.ocrImages(images)
    }

    private suspend fun ocrImage(file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法解析图片" }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) > 1800) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: error("无法读取图片")
        return try {
            aiClient.ocrImages(listOf(bitmap.toJpeg(86)))
        } finally {
            bitmap.recycle()
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun Bitmap.toJpeg(quality: Int): ByteArray = ByteArrayOutputStream().use { output ->
        compress(Bitmap.CompressFormat.JPEG, quality, output)
        output.toByteArray()
    }

    private fun extractDocx(file: File): String {
        val xml = file.inputStream().use(::findDocumentXml)
            ?: error("DOCX 中缺少正文内容")
        return parseWordXml(xml)
    }

    private fun findDocumentXml(input: InputStream): ByteArray? {
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    return zip.readBytes().takeIf { it.size <= MAX_DOCX_XML_BYTES }
                        ?: error("DOCX 正文过大")
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun parseWordXml(xml: ByteArray): String {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.inputStream(), null)
        val output = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "tab" -> output.append('\t')
                    "br" -> output.append('\n')
                }
                XmlPullParser.TEXT -> output.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "p", "tr" -> if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
                }
            }
            event = parser.next()
            if (output.length >= MAX_TEXT_CHARS) break
        }
        return output.toString()
            .replace(Regex("[\\t ]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(MAX_TEXT_CHARS)
    }

    companion object {
        private const val MAX_PDF_PAGES = 8
        private const val MAX_TEXT_CHARS = 80_000
        private const val MAX_DOCX_XML_BYTES = 8 * 1024 * 1024
    }
}
