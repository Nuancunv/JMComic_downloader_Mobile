package com.nuancun.jmcomic.mobile.reader

import com.nuancun.jmcomic.mobile.data.ChapterEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.PageEntity

data class ReadingPosition(val page: Int, val chapterId: String? = null, val chapterPage: Int = 0)
data class ReadingPage(val position: ReadingPosition, val path: String)

/** Only complete chapters are published; page identities do not depend on completion order. */
class ReadingIndex private constructor(val entries: List<ReadingPage>) {
    constructor(chapters: List<ChapterEntity>, pages: List<PageEntity>) : this(buildEntries(chapters, pages))

    /** Keep published pages during revalidation; add newly completed chapters without losing identity. */
    fun retaining(previous: ReadingIndex?): ReadingIndex = ReadingIndex(
        (previous?.entries.orEmpty() + entries).associateBy { it.position.chapterId to it.position.chapterPage }
            .values.sortedBy { it.position.page },
    )

    companion object {
        private fun buildEntries(chapters: List<ChapterEntity>, pages: List<PageEntity>): List<ReadingPage> {
            val byChapter = pages.groupBy { it.chapterId }
            var start = 0
            return buildList {
                chapters.sortedBy { it.chapterOrder }.forEach { chapter ->
                    val saved = byChapter[chapter.chapterId].orEmpty().sortedBy { it.pageIndex }
                    if (chapter.status == DownloadState.COMPLETE && chapter.pageCount > 0 &&
                        saved.size == chapter.pageCount && saved.withIndex().all { (index, page) ->
                            page.pageIndex == index + 1 && page.status == DownloadState.COMPLETE && !page.localPath.isNullOrBlank()
                        }) {
                        saved.forEach { page ->
                            add(ReadingPage(ReadingPosition(start + page.pageIndex - 1, chapter.chapterId, page.pageIndex - 1), page.localPath!!))
                        }
                    }
                    start += chapter.pageCount
                }
            }
        }
    }

    fun indexOf(position: ReadingPosition): Int {
        if (position.chapterId != null) {
            val exact = entries.indexOfFirst { it.position.chapterId == position.chapterId && it.position.chapterPage == position.chapterPage }
            if (exact >= 0) return exact
            val chapter = entries.indexOfFirst { it.position.chapterId == position.chapterId }
            if (chapter >= 0) return chapter
        }
        return entries.indexOfLast { it.position.page <= position.page }.coerceAtLeast(0)
    }
}
