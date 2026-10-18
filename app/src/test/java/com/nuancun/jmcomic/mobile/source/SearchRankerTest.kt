package com.nuancun.jmcomic.mobile.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankerTest {
    @Test fun exactAndContinuousTitleMatchesRankFirst() {
        val hits = listOf(
            SearchHit("1", "故事里的测试", "测试作者", emptyList()),
            SearchHit("2", "测试漫画", "其他", emptyList()),
            SearchHit("3", "测试", "其他", emptyList()),
        )
        assertEquals(listOf("3", "2", "1"), SearchRanker.rank("测试", hits).map { it.id })
    }

    @Test fun fullWidthInputAndStableTiesAreHandled() {
        val hits = listOf(
            SearchHit("10", "ABC", "", emptyList()),
            SearchHit("11", "ＡＢＣ", "", emptyList()),
        )
        assertEquals(listOf("10", "11"), SearchRanker.rank("ＡＢＣ", hits).map { it.id })
    }

    @Test fun exactIdOutranksTextMatches() {
        val hits = listOf(
            SearchHit("7", "作品 123", "", emptyList()),
            SearchHit("123", "其他作品", "", emptyList()),
        )
        assertEquals("123", SearchRanker.rank("123", hits).first().id)
    }

    @Test fun exactScriptMatchOutranksEquivalentAndMetadataMatches() {
        val hits = listOf(
            SearchHit("1", "其他故事", "銀河圖書館", listOf("銀河圖書館")),
            SearchHit("2", "銀河圖書館", "", emptyList()),
            SearchHit("3", "银河图书馆", "", emptyList()),
        )
        assertEquals(listOf("3", "2", "1"), SearchRanker.rank("银河图书馆", hits).map { it.id })
    }

    @Test fun multipleKeywordsAndMixedMetadataAreMatched() {
        val query = QueryAnalyzer.analyze("银河 航线")
        assertEquals(MatchKind.TITLE_WORDS, SearchRanker.match(query, SearchDocument("1", "航线之外的银河", "", emptyList())).kind)
        assertEquals(MatchKind.FIELDS, SearchRanker.match(query, SearchDocument("2", "银河旅人", "航线", emptyList())).kind)
    }

    @Test fun typoMatchingIsConservativeForShortQueries() {
        val book = SearchDocument("1", "银河旅人", "", emptyList())
        assertEquals(MatchKind.TITLE_SIMILAR, SearchRanker.match(QueryAnalyzer.analyze("银河侣人"), book).kind)
        assertEquals(MatchKind.NONE, SearchRanker.match(QueryAnalyzer.analyze("银河人"), book).kind)
        assertEquals(MatchKind.NONE, SearchRanker.match(QueryAnalyzer.analyze("月海"), book).kind)
    }

    @Test fun literalWildcardsDoNotMatchUnrelatedBooks() {
        val book = SearchDocument("1", "银河旅人", "", emptyList())
        assertEquals(MatchKind.NONE, SearchRanker.match(QueryAnalyzer.analyze("%"), book).kind)
        assertEquals(MatchKind.NONE, SearchRanker.match(QueryAnalyzer.analyze("_"), book).kind)
        assertEquals(MatchKind.TITLE_PHRASE, SearchRanker.match(QueryAnalyzer.analyze("%"), book.copy(title = "100%远航")).kind)
    }

    @Test fun allAuthorsAndChapterReasonsAreRetained() {
        val hit = SearchHit("1", "其他故事", "甲", emptyList(), authors = listOf("甲", "银河"))
        assertEquals(MatchKind.AUTHOR, SearchRanker.rank("银河", listOf(hit)).single().match?.kind)
        val chapter = SearchRanker.match(QueryAnalyzer.analyze("银河"), SearchDocument("2", "航海日记", "", emptyList(), listOf("第一章 银河之门")))
        assertEquals(MatchKind.CHAPTER, chapter.kind)
        assertTrue(chapter.reason.contains("第一章 银河之门"))
    }

    @Test fun exactTitleCannotBeOvertakenByManyTags() {
        val weak = SearchHit("1", "其他故事", "银河", List(1000) { "银河" })
        val exact = SearchHit("2", "银河", "", emptyList())
        assertEquals("2", SearchRanker.rank("银河", listOf(weak, exact)).first().id)
    }

    @Test fun highlightRangesUseOriginalDisplayIndices() {
        assertEquals(listOf(MatchRange(2, 5)), SearchRanker.highlights("😀ＡＢＣ航线", QueryAnalyzer.analyze("abc")))
        assertEquals(listOf(MatchRange(0, 5)), SearchRanker.highlights("銀 河圖書館", QueryAnalyzer.analyze("银河图书")))
        val text = "银河 银河"
        assertEquals(listOf(MatchRange(0, 2), MatchRange(3, 5)), SearchRanker.highlights(text, QueryAnalyzer.analyze("银河")))
    }
}
