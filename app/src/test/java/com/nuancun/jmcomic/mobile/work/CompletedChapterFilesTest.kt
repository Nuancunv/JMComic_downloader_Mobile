package com.nuancun.jmcomic.mobile.work

import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.data.ChapterEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.PageEntity
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompletedChapterFilesTest {
    @get:Rule val folder = TemporaryFolder()
    private val album = AlbumEntity("123", "fixture", "author", "", "", DownloadState.FAILED,
        1, 1, null, 0, addedAt = 0, updatedAt = 0)
    private val chapter = ChapterEntity("456", "123", 1, "chapter", DownloadState.COMPLETE, 2, 2, null)

    private fun files(): List<PageEntity> = (1..2).map { index ->
        val file = File(folder.root, "chapters/456/images/%06d.jpg".format(index))
        file.parentFile!!.mkdirs()
        file.writeText("synthetic page $index")
        PageEntity("456:$index", "456", index, "$index.jpg", file.absolutePath,
            CompletedChapterFiles.sha256(file), file.length(), DownloadState.COMPLETE)
    }

    @Test fun completeSnapshotCanResumePdfUsingOnlyLocalFiles() = runBlocking {
        val pages = files()
        val inputs = CompletedChapterFiles.restore(album, listOf(chapter), folder.root, { pages }, { true }, {})!!
        assertEquals("456", inputs.single().chapterId)
        assertEquals(listOf("000001.jpg", "000002.jpg"), inputs.single().images.map { it.name })
    }

    @Test fun refusesIncompleteAlbumOrChapterWithoutInventingMissingPages() = runBlocking {
        val pages = files()
        assertNull(CompletedChapterFiles.restore(album.copy(totalChapters = 2), listOf(chapter), folder.root, { pages }, { true }, {}))
        assertNull(CompletedChapterFiles.restore(album, listOf(chapter.copy(completedPages = 1)), folder.root, { pages }, { true }, {}))
        assertNull(CompletedChapterFiles.restore(album, listOf(chapter.copy(status = DownloadState.DOWNLOADING)), folder.root, { pages }, { true }, {}))
    }

    @Test fun refusesMissingOrNoncontiguousPageRecords() = runBlocking {
        val pages = files()
        assertNull(CompletedChapterFiles.restore(album, listOf(chapter), folder.root, { pages.take(1) }, { true }, {}))
        assertNull(CompletedChapterFiles.restore(album, listOf(chapter), folder.root, { pages.map { it.copy(pageIndex = 2) } }, { true }, {}))
    }

    @Test fun refusesChangedBytesEvenWhenFileSizeIsUnchanged() = runBlocking {
        val pages = files()
        val file = File(pages[0].localPath!!)
        file.writeText("X".repeat(file.length().toInt()))
        assertNull(CompletedChapterFiles.restore(album, listOf(chapter), folder.root, { pages }, { true }, {}))
    }

    @Test fun refusesOutsidePathAndInvalidImages() = runBlocking {
        val pages = files()
        val outside = folder.newFile("outside.jpg")
        assertNull(CompletedChapterFiles.restore(album, listOf(chapter), folder.root,
            { pages.map { it.copy(localPath = outside.absolutePath) } }, { true }, {}))
        assertNull(CompletedChapterFiles.restore(album, listOf(chapter), folder.root, { pages }, { false }, {}))
    }

    @Test fun cancellationPropagatesWhileCheckingLocalSnapshot() = runBlocking {
        val pages = files()
        try {
            CompletedChapterFiles.restore(album, listOf(chapter), folder.root, { pages }, { true }) {
                throw CancellationException("fixture cancellation")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun schedulerDoesNotBlockCompletedPdfRetryOnConnectivity() {
        assertFalse(DownloadScheduler.requiresNetwork(album, listOf(chapter)))
        assertTrue(DownloadScheduler.requiresNetwork(null, emptyList()))
        assertTrue(DownloadScheduler.requiresNetwork(album, listOf(chapter.copy(completedPages = 1))))
        val pdf = folder.newFile("published.pdf")
        assertTrue(DownloadScheduler.requiresNetwork(album.copy(status = DownloadState.COMPLETE, pdfPath = pdf.absolutePath), listOf(chapter)))
    }
}
