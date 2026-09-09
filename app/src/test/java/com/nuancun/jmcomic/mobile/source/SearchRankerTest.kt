package com.nuancun.jmcomic.mobile.source

import org.junit.Assert.assertEquals
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
}
