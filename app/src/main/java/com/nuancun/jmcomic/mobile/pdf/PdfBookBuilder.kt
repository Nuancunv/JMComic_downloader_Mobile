package com.nuancun.jmcomic.mobile.pdf

import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.io.RandomAccessBufferedFileInputStream
import com.tom_roush.pdfbox.io.RandomAccessRead
import com.tom_roush.pdfbox.io.ScratchFile
import com.tom_roush.pdfbox.pdfparser.PDFParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageTree
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ChapterPdfInput(val chapterId: String, val title: String, val images: List<File>)
data class PdfBuildResult(val pageCount: Int, val chapterStartPages: Map<String, Int>)

object PdfBookBuilder {
    fun build(albumTitle: String, chapters: List<ChapterPdfInput>, destination: File, checkActive: () -> Unit = {}): PdfBuildResult {
        require(chapters.isNotEmpty()) { "没有可生成 PDF 的章节" }
        require(chapters.all { it.images.isNotEmpty() }) { "章节图片不完整" }
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, "${destination.name}.part")
        if (temp.exists()) temp.delete()
        val starts = linkedMapOf<String, Int>()
        var totalPages = 0

        try {
            PDDocument(memoryUsage(temp.parentFile)).use { document ->
                document.documentInformation.title = albumTitle
                val outline = PDDocumentOutline()
                document.documentCatalog.documentOutline = outline

                chapters.forEach { chapter ->
                    starts[chapter.chapterId] = totalPages + 1
                    var firstPage: PDPage? = null
                    chapter.images.forEach { file ->
                        checkActive()
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, bounds)
                        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片：${file.name}" }
                        val pageScale = minOf(1f, 14_000f / maxOf(bounds.outWidth, bounds.outHeight).toFloat())
                        val width = (bounds.outWidth * pageScale).coerceAtLeast(1f)
                        val height = (bounds.outHeight * pageScale).coerceAtLeast(1f)
                        val page = PDPage(PDRectangle(width, height))
                        document.addPage(page)
                        if (firstPage == null) firstPage = page
                        // DownloadWorker has already normalized every page to RGB JPEG. Copy its
                        // compressed stream into the bounded scratch store without a whole-file byte array.
                        val image = file.inputStream().buffered().use { input ->
                            PDImageXObject(document, CancellableInput(input, checkActive), COSName.DCT_DECODE,
                                bounds.outWidth, bounds.outHeight, 8, PDDeviceRGB.INSTANCE)
                        }
                        PDPageContentStream(document, page).use { stream ->
                            stream.drawImage(image, 0f, 0f, width, height)
                        }
                        totalPages++
                    }
                    val destinationPage = firstPage ?: error("章节没有页面")
                    val pageDestination = PDPageFitDestination().apply { page = destinationPage }
                    outline.addLast(PDOutlineItem().apply {
                        title = chapter.title
                        this.destination = pageDestination
                    })
                }
                outline.openNode()
                checkActive()
                FileOutputStream(temp).use { file ->
                    document.save(CancellableOutput(file.buffered(64 * 1024), checkActive))
                    checkActive()
                    file.fd.sync()
                }
            }

            checkActive()
            loadForValidation(temp, checkActive).use { validation ->
                require(validation.numberOfPages == totalPages) { "PDF 页数校验失败" }
                require(validation.documentCatalog.documentOutline?.firstChild != null) { "PDF 章节目录校验失败" }
                validatePages(validation.pages, checkActive)
            }
            checkActive()
            atomicReplace(temp, destination)
            return PdfBuildResult(totalPages, starts)
        } finally { temp.delete() }
    }

    internal fun validatePages(pages: PDPageTree, checkActive: () -> Unit) {
        var count = 0
        for (page in pages) { checkActive(); requireNotNull(page); count++ }
        require(count == pages.count) { "PDF 页数校验失败" }
    }

    private fun memoryUsage(directory: File?): MemoryUsageSetting =
        MemoryUsageSetting.setupMixed(4L * 1024 * 1024).setTempDir(directory)

    private fun loadForValidation(file: File, checkActive: () -> Unit): PDDocument {
        val source = CancellableRead(RandomAccessBufferedFileInputStream(file), checkActive)
        try {
            val scratch = ScratchFile(memoryUsage(file.parentFile))
            try {
                val parser = PDFParser(source, scratch)
                parser.parse()
                return parser.pdDocument
            } catch (error: Throwable) { scratch.close(); throw error }
        } catch (error: Throwable) { source.close(); throw error }
    }

    private class CancellableRead(private val source: RandomAccessRead, private val checkActive: () -> Unit) : RandomAccessRead by source {
        override fun read(): Int { checkActive(); return source.read() }
        override fun read(bytes: ByteArray): Int { checkActive(); return source.read(bytes) }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int { checkActive(); return source.read(bytes, offset, length) }
        override fun readFully(length: Int): ByteArray { checkActive(); return source.readFully(length) }
        override fun seek(position: Long) { checkActive(); source.seek(position) }
        override fun peek(): Int { checkActive(); return source.peek() }
    }

    private class CancellableInput(input: java.io.InputStream, private val checkActive: () -> Unit) : java.io.FilterInputStream(input) {
        override fun read(): Int { checkActive(); return `in`.read() }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            checkActive()
            return `in`.read(bytes, offset, minOf(length, 64 * 1024))
        }
    }

    private class CancellableOutput(output: OutputStream, private val checkActive: () -> Unit) : FilterOutputStream(output) {
        // COSWriter closes its output; the outer FileOutputStream owns the descriptor and sync.
        override fun close() { flush() }
        override fun write(value: Int) { checkActive(); out.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var written = 0
            while (written < length) {
                checkActive()
                val count = minOf(length - written, 64 * 1024)
                out.write(bytes, offset + written, count)
                written += count
            }
        }
    }

    private fun atomicReplace(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            // Never delete the published document before its replacement has succeeded.
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
