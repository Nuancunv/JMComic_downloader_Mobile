package com.nuancun.jmcomic.mobile.reader

import com.nuancun.jmcomic.mobile.data.ChapterEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.PageEntity
import org.junit.Assert.*
import org.junit.Test

class ReadingIndexTest {
    private fun chapter(id: String, order: Int, count: Int, ready: Boolean = true) =
        ChapterEntity(id, "book", order, id, if (ready) DownloadState.COMPLETE else DownloadState.DOWNLOADING,
            count, if (ready) count else 0, null)
    private fun page(id: String, index: Int) = PageEntity("$id:$index", id, index, "$index.jpg", "/$id/$index.jpg", "sha", 1, DownloadState.COMPLETE)

    @Test fun completedChapterCanBeReadBeforeBookOrPdfIsComplete() {
        val index = ReadingIndex(listOf(chapter("a", 1, 2), chapter("b", 2, 2, false)),
            listOf(page("a", 2), page("b", 1), page("a", 1)))
        assertEquals(listOf("/a/1.jpg", "/a/2.jpg"), index.entries.map { it.path })
        assertEquals(1, index.indexOf(ReadingPosition(1)))
    }

    @Test fun completeFlagAloneCannotPublishMissingDuplicateOrUncommittedPages() {
        val chapters = listOf(chapter("a", 1, 2))
        assertTrue(ReadingIndex(chapters, listOf(page("a", 1))).entries.isEmpty())
        assertTrue(ReadingIndex(chapters, listOf(page("a", 1), page("a", 1))).entries.isEmpty())
        assertTrue(ReadingIndex(chapters, listOf(page("a", 1), page("a", 2).copy(status = "DOWNLOADING"))).entries.isEmpty())
        assertTrue(ReadingIndex(chapters, listOf(page("a", 1), page("a", 2).copy(localPath = null))).entries.isEmpty())
    }

    @Test fun chapterBookmarkSurvivesInsertionAndGlobalPageChanges() {
        val chapters = listOf(chapter("a", 1, 2, false), chapter("b", 2, 2))
        val old = ReadingIndex(chapters, listOf(page("b", 2), page("b", 1)))
        val bookmark = old.entries[1].position
        val next = ReadingIndex(listOf(chapter("b", 2, 2), chapter("a", 1, 3)),
            listOf(page("a", 1), page("a", 2), page("a", 3), page("b", 1), page("b", 2)))
        assertEquals(4, next.indexOf(bookmark))
        assertEquals("/b/2.jpg", next.entries[next.indexOf(bookmark)].path)
    }

    @Test fun revalidationKeepsAlreadyPublishedPagesAndAddsNewChapters() {
        val previous = ReadingIndex(listOf(chapter("a", 1, 1)), listOf(page("a", 1)))
        val next = ReadingIndex(listOf(chapter("a", 1, 1, false), chapter("b", 2, 1)), listOf(page("b", 1)))
            .retaining(previous)
        assertEquals(listOf("/a/1.jpg", "/b/1.jpg"), next.entries.map { it.path })
    }

    @Test fun legacyGlobalBookmarkAndMissingChapterAreClamped() {
        val index = ReadingIndex(listOf(chapter("a", 1, 2)), listOf(page("a", 1), page("a", 2)))
        assertEquals(1, index.indexOf(ReadingPosition(1)))
        assertEquals(0, index.indexOf(ReadingPosition(-1, "missing")))
        assertEquals(1, index.indexOf(ReadingPosition(999)))
        assertEquals(0, index.indexOf(ReadingPosition(999, "a", 50)))
    }
}
