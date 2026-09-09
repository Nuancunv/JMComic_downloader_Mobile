package com.nuancun.jmcomic.mobile.pdf

import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageTree
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ChapterPdfInput(val chapterId: String, val title: String, val images: List<File>)
data class PdfBuildResult(val pageCount: Int, val chapterStartPages: Map<String, Int>)

object PdfBookBuilder {
    fun build(albumTitle: String, chapters: List<ChapterPdfInput>, destination: File): PdfBuildResult {
        require(chapters.isNotEmpty()) { "没有可生成 PDF 的章节" }
        require(chapters.all { it.images.isNotEmpty() }) { "章节图片不完整" }
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, "${destination.name}.part")
        if (temp.exists()) temp.delete()
        val starts = linkedMapOf<String, Int>()
        var totalPages = 0

        PDDocument().use { document ->
            document.documentInformation.title = albumTitle
            val outline = PDDocumentOutline()
            document.documentCatalog.documentOutline = outline

            chapters.forEach { chapter ->
                starts[chapter.chapterId] = totalPages + 1
                var firstPage: PDPage? = null
                chapter.images.forEach { file ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片：${file.name}" }
                    val pageScale = minOf(1f, 14_000f / maxOf(bounds.outWidth, bounds.outHeight).toFloat())
                    val width = (bounds.outWidth * pageScale).coerceAtLeast(1f)
                    val height = (bounds.outHeight * pageScale).coerceAtLeast(1f)
                    val page = PDPage(PDRectangle(width, height))
                    document.addPage(page)
                    if (firstPage == null) firstPage = page
                    val image = file.inputStream().use { JPEGFactory.createFromStream(document, it) }
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
            document.save(temp)
        }

        PDDocument.load(temp).use { validation ->
            require(validation.numberOfPages == totalPages) { "PDF 页数校验失败" }
            require(validation.documentCatalog.documentOutline?.firstChild != null) { "PDF 章节目录校验失败" }
            val pages: PDPageTree = validation.pages
            for (index in 0 until pages.count) requireNotNull(pages[index])
        }
        atomicReplace(temp, destination)
        return PdfBuildResult(totalPages, starts)
    }

    private fun atomicReplace(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            if (destination.exists() && !destination.delete()) error("无法替换旧 PDF")
            if (!source.renameTo(destination)) error("无法发布 PDF")
        }
    }
}
