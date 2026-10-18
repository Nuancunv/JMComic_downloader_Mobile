package com.nuancun.jmcomic.mobile.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfBookBuilderTest {
    @get:Rule val folder = TemporaryFolder()

    private fun jpeg(): File = folder.newFile("page.jpg").also { file ->
        javaClass.getResourceAsStream("/pdf/page.jpg")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    }

    @Test fun validatesPageTreeWithLinearVisits() {
        class CountingKids : COSArray() {
            var visits = 0
            override fun getObject(index: Int): COSBase { visits++; return super.getObject(index) }
        }
        PDDocument().use { document ->
            repeat(300) { document.addPage(PDPage()) }
            val kids = CountingKids()
            kids.addAll(document.pages.cosObject.getDictionaryObject(COSName.KIDS) as COSArray)
            document.pages.cosObject.setItem(COSName.KIDS, kids)
            var checked = 0
            PdfBookBuilder.validatePages(document.pages) { checked++ }
            println("300-page validation: visits=${kids.visits}")
            assertEquals(300, checked)
            assertTrue("Page visits must be linear, actual=${kids.visits}", kids.visits <= 900)
        }
    }

    @Test fun failedPublicationPreservesExistingPdf() {
        val destination = folder.newFile("book.pdf").apply { writeText("previous valid PDF") }
        val missing = File(folder.root, "missing.part")
        val method = PdfBookBuilder::class.java.getDeclaredMethod("atomicReplace", File::class.java, File::class.java)
        method.isAccessible = true
        try { method.invoke(PdfBookBuilder, missing, destination); fail("Missing source must fail") }
        catch (_: InvocationTargetException) { /* The old published document must survive. */ }
        assertEquals("previous valid PDF", destination.readText())
    }

    @Test fun cancellationAfterSaveCleansPartAndPreservesPreviousPdf() {
        val destination = folder.newFile("book.pdf").apply { writeText("previous valid PDF") }
        val part = File(folder.root, "book.pdf.part")
        try {
            PdfBookBuilder.build("fixture", listOf(ChapterPdfInput("123", "chapter", listOf(jpeg()))), destination) {
                if (part.length() > 0) throw CancellationException("fixture cancellation")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse("Cancelled PDF must not leave a partial file", part.exists())
        assertEquals("previous valid PDF", destination.readText())
    }

    @Test fun producesReadablePdfWithOrderedPagesBookmarksAndOriginalJpeg() {
        val image = jpeg()
        val output = File(folder.root, "book.pdf")
        val result = PdfBookBuilder.build("fixture", listOf(
            ChapterPdfInput("123", "第一章", listOf(image, image)),
            ChapterPdfInput("456", "第二章", listOf(image)),
        ), output)
        assertEquals(3, result.pageCount)
        assertEquals(mapOf("123" to 1, "456" to 3), result.chapterStartPages)
        PDDocument.load(output).use { document ->
            assertEquals(3, document.numberOfPages)
            assertEquals("fixture", document.documentInformation.title)
            val outline = document.documentCatalog.documentOutline!!
            assertEquals("第一章", outline.firstChild.title)
            assertEquals("第二章", outline.firstChild.nextSibling.title)
            document.pages.forEach { page ->
                assertEquals(40f, page.mediaBox.width)
                assertEquals(60f, page.mediaBox.height)
                val embedded = page.resources.getXObject(page.resources.xObjectNames.first())!!
                val stream = embedded.cosObject as com.tom_roush.pdfbox.cos.COSStream
                assertArrayEquals(image.readBytes(), stream.createRawInputStream().use { it.readBytes() })
            }
        }
        val qa = File("build/pdf-qa").apply { mkdirs() }
        output.copyTo(File(qa, "ordered-book.pdf"), overwrite = true)
    }

    @Test fun spillsLargeBookStreamsToDiskAndCleansScratch() {
        val file = folder.newFile("large.jpg")
        javaClass.getResourceAsStream("/pdf/large.jpg")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        var sawScratch = false
        val output = File(folder.root, "book.pdf")
        PdfBookBuilder.build("fixture", listOf(ChapterPdfInput("123", "chapter", List(80) { file })), output) {
            sawScratch = sawScratch || folder.root.listFiles().orEmpty().any { it.name.startsWith("PDFBox") && it.length() > 0 }
        }
        assertTrue("Large book must use disk scratch rather than retain every JPEG on heap", sawScratch)
        assertTrue(folder.root.listFiles().orEmpty().none { it.name.startsWith("PDFBox") })
    }

    @Test fun cancellationDuringValidationClosesFileAndScratch() {
        val output = File(folder.root, "book.pdf")
        PdfBookBuilder.build("fixture", listOf(ChapterPdfInput("123", "chapter", listOf(jpeg()))), output)
        val method = PdfBookBuilder::class.java.getDeclaredMethod("loadForValidation", File::class.java, kotlin.jvm.functions.Function0::class.java)
        method.isAccessible = true
        var checks = 0
        val callback: () -> Unit = { if (++checks == 10) throw CancellationException("fixture cancellation") }
        try { method.invoke(PdfBookBuilder, output, callback); fail("Validation must be cancellable") }
        catch (error: InvocationTargetException) { assertTrue(error.cause is CancellationException) }
        assertEquals(10, checks)
        assertTrue(output.renameTo(File(folder.root, "renamed.pdf")))
        assertTrue(folder.root.listFiles().orEmpty().none { it.name.startsWith("PDFBox") })
    }

    @Test fun invalidImageLeavesPublishedPdfUntouchedAndCleansPart() {
        val output = folder.newFile("book.pdf").apply { writeText("previous valid PDF") }
        val bad = folder.newFile("bad.jpg").apply { writeText("not a JPEG") }
        try {
            PdfBookBuilder.build("fixture", listOf(ChapterPdfInput("123", "chapter", listOf(bad))), output)
            fail("Invalid image must fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals("previous valid PDF", output.readText())
        assertFalse(File(folder.root, "book.pdf.part").exists())
    }
}
