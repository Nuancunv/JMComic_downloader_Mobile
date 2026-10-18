package com.nuancun.jmcomic.mobile.source

import org.junit.Assert.*
import org.junit.Test

class QueryAnalyzerTest {
    @Test fun analyzesFullWidthIdsAndLinksWithoutPrefixMatches() {
        listOf("ＪＭ１２３", "jm123", "123", "https://example.test/album/123/?x=1", "https://example.test/photo/123#chapter")
            .forEach { assertEquals(it, "123", QueryAnalyzer.analyze(it).id) }
        assertNull(QueryAnalyzer.analyze("https://example.test/album/123abc").id)
        assertNull(QueryAnalyzer.analyze("1234567890123").id)
    }

    @Test fun normalizesWhitespaceCaseAndSeparators() {
        val query = QueryAnalyzer.analyze("  ＲＥＤ\tＲＩＶＥＲ：远航  ")
        assertEquals("red river 远航", query.key)
        assertEquals(listOf("red", "river", "远航"), query.words)
    }

    @Test fun keepsOriginalQueryAndOffersBoundedScriptVariants() {
        val query = QueryAnalyzer.analyze("銀河圖書館")
        assertEquals("銀河圖書館", query.text)
        assertEquals("银河图书馆", query.foldedKey)
        val variants = QueryAnalyzer.remoteQueries(query)
        assertEquals("銀河圖書館", variants.first())
        assertTrue(variants.contains("银河图书馆"))
        assertTrue(variants.size <= 3)
        assertEquals(variants.distinct(), variants)
    }

    @Test fun idHasNoExpansionAndBlankHasNoRequest() {
        assertEquals(listOf("123"), QueryAnalyzer.remoteQueries(QueryAnalyzer.analyze("JM123")))
        assertTrue(QueryAnalyzer.remoteQueries(QueryAnalyzer.analyze("  ")).isEmpty())
    }

    @Test fun latinWordBoundariesAreNotRemoved() {
        assertNotEquals(QueryAnalyzer.analyze("red river").compactKey, QueryAnalyzer.analyze("redriver").compactKey)
        assertEquals("银河旅人", QueryAnalyzer.analyze("银 河 旅 人").compactKey)
    }

    @Test fun punctuationQueriesStayLiteralAndInputIsBounded() {
        assertFalse(QueryAnalyzer.analyze("%").isEmpty)
        assertEquals("%", QueryAnalyzer.analyze("%").key)
        assertEquals("a_b", QueryAnalyzer.analyze("Ａ_Ｂ").key)
        assertEquals(QueryAnalyzer.MAX_QUERY_LENGTH, QueryAnalyzer.analyze("字".repeat(1000)).text.length)
    }
}
