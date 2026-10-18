package com.nuancun.jmcomic.mobile.ui

import org.junit.Assert.*
import org.junit.Test

class FirstByIdIndexTest {
    private data class Entry(val id: String, val value: Int)

    @Test fun newestFirstEntryWinsIncludingDuplicates() {
        val index = FirstByIdIndex(listOf(Entry("42", 2), Entry("42", 1), Entry("43", 3))) { it.id }
        assertEquals(2, index["42"]?.value)
        assertEquals(3, index["43"]?.value)
        assertNull(index["missing"])
    }

    @Test fun repeatedVisibleRowLookupsDoNotRescanWholeLibrary() {
        var idReads = 0
        val entries = List(5_000) { Entry(it.toString(), it) }
        val index = FirstByIdIndex(entries) { idReads++; it.id }
        repeat(200) { assertEquals(4_999, index["4999"]?.value) }
        println("lookup workload: 5000 entries, 200 lookups, $idReads ID reads")
        assertTrue("Expected one indexing pass, observed $idReads ID reads", idReads <= entries.size)
    }
}
