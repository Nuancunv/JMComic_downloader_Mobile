package com.nuancun.jmcomic.mobile.data

import com.nuancun.jmcomic.mobile.source.MatchKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalSearchRepositoryTest {
    private fun book(id: String, title: String, updated: Long = 0, author: String = "", tags: String = "", chapters: List<String> = emptyList()) =
        SearchableAlbum(AlbumEntity(id, title, author, tags, "", DownloadState.COMPLETE, 1, 1, null, 0,
            addedAt = 0, updatedAt = updated), chapters)

    @Test fun exactTitleOutranksRecentWeakMatches() {
        val entries = listOf(book("1", "其他故事", 1000, tags = "银河旅人"), book("2", "银河旅人", 1))
        assertEquals(listOf("2", "1"), LocalSearchRepository.search("银河旅人", entries).map { it.album.albumId })
    }

    @Test fun emptyQueryKeepsRecentOrderAndSearchIsLiteral() {
        val entries = listOf(book("1", "100%远航", 1), book("2", "普通作品", 2))
        assertEquals(listOf("2", "1"), LocalSearchRepository.search("", entries).map { it.album.albumId })
        assertEquals(listOf("1"), LocalSearchRepository.search("%", entries).map { it.album.albumId })
        assertTrue(LocalSearchRepository.search("_", entries).isEmpty())
    }

    @Test fun supportsScriptWhitespaceAndReorderedTitleWords() {
        val entries = listOf(book("1", "银河图书馆：远航"))
        for (query in listOf("銀河圖書館", "银 河 图 书 馆", "远航 银河", "银河图书馆 远航")) {
            assertEquals(query, "1", LocalSearchRepository.search(query, entries).single().album.albumId)
        }
    }

    @Test fun matchesAuthorTagsChaptersAndIds() {
        val entries = listOf(book("123", "航海日记", author = "星河", tags = "科幻 旅行", chapters = listOf("第五章 月球旅程")))
        assertEquals(MatchKind.AUTHOR, LocalSearchRepository.search("星河", entries).single().match.kind)
        assertEquals(MatchKind.TAG, LocalSearchRepository.search("科幻", entries).single().match.kind)
        assertEquals(MatchKind.CHAPTER, LocalSearchRepository.search("月球", entries).single().match.kind)
        assertEquals(MatchKind.ID, LocalSearchRepository.search("JM123", entries).single().match.kind)
        assertTrue(LocalSearchRepository.search("不存在的文字", entries).isEmpty())
    }

    @Test fun chapterMatchesDoNotCrossSeparateChapters() {
        val entries = listOf(book("1", "航海日记", chapters = listOf("银河之门", "远航开始")))
        assertTrue(LocalSearchRepository.search("银河 远航", entries).isEmpty())
    }

    @Test fun repositorySelectsTheRequestedArchiveScope() = runBlocking {
        val scopes = mutableListOf<Boolean>()
        val repository = LocalSearchRepository { archived ->
            scopes.add(archived)
            flowOf(listOf(book(if (archived) "2" else "1", "银河旅人")))
        }
        assertEquals("1", repository.observe(flowOf("银河"), false).first().single().album.albumId)
        assertEquals("2", repository.observe(flowOf("银河"), true).first().single().album.albumId)
        assertEquals(listOf(false, true), scopes)
    }
}
