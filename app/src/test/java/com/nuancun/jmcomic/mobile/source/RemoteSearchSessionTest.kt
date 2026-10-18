package com.nuancun.jmcomic.mobile.source

import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class RemoteSearchSessionTest {
    private fun hit(id: String, title: String = "银河旅人") = SearchHit(id, title, "", emptyList())

    @Test fun normalizedExpansionCanRecallAMissingTitle() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val session = RemoteSearchSession("銀河旅人", SearchSource { query, _ ->
            calls.add(query)
            SearchPage(if (query == "银河旅人") listOf(hit("2")) else emptyList(), hasMore = false)
        })
        val batch = session.loadNext()
        assertEquals(listOf("2"), batch.hits.map { it.id })
        assertTrue(calls.contains("銀河旅人"))
        assertTrue(calls.contains("银河旅人"))
        assertFalse(batch.hasMore)
        assertEquals("银河旅人", batch.hits.single().origins.single().query)
    }

    @Test fun exactTitleSkipsUnnecessaryExpansionAndPrefetch() = runBlocking {
        val calls = AtomicInteger()
        val session = RemoteSearchSession("銀河旅人", SearchSource { _, _ ->
            calls.incrementAndGet(); SearchPage(listOf(hit("1", "銀河旅人")), hasMore = false)
        })
        assertEquals("1", session.loadNext().hits.single().id)
        assertEquals(1, calls.get())
    }

    @Test fun missingExactTitlePrefetchesSecondPrimaryPage() = runBlocking {
        val pages = mutableListOf<Int>()
        val session = RemoteSearchSession("银河旅人", SearchSource { _, page ->
            pages.add(page)
            SearchPage(listOf(hit(page.toString(), if (page == 1) "银河旅人续篇" else "银河旅人")), hasMore = page == 1)
        })
        assertEquals(listOf("2", "1"), session.loadNext().hits.map { it.id })
        assertEquals(listOf(1, 2), pages)
    }

    @Test fun appendedPagesDoNotReorderPreviouslyDisplayedItems() = runBlocking {
        val session = RemoteSearchSession("银河旅人", SearchSource { _, page ->
            SearchPage(listOf(hit(page.toString(), if (page < 3) "银河旅人续篇$page" else "银河旅人")), hasMore = page < 3)
        })
        val first = session.loadNext()
        assertEquals(listOf("1", "2"), first.hits.map { it.id })
        assertTrue(first.hasMore)
        val next = session.loadNext()
        assertEquals(listOf("1", "2", "3"), next.hits.map { it.id })
        assertEquals(MatchKind.TITLE_EXACT, next.hits.last().match?.kind)
        assertFalse(next.hasMore)
    }

    @Test fun firstPageDuplicatesMergeAuthorsTagsAndProvenance() = runBlocking {
        val session = RemoteSearchSession("银河旅人", SearchSource { _, _ ->
            SearchPage(listOf(
                hit("1").copy(author = "甲", authors = listOf("甲"), tags = listOf("旅行")),
                hit("1").copy(author = "乙", authors = listOf("乙"), tags = listOf("科幻")),
            ), hasMore = false)
        })
        val result = session.loadNext().hits.single()
        assertEquals(listOf("甲", "乙"), result.authors)
        assertEquals(listOf("旅行", "科幻"), result.tags)
        assertEquals(2, result.origins.size)
    }

    @Test fun repeatedPageStopsEvenWhenServerClaimsMore() = runBlocking {
        val calls = AtomicInteger()
        val session = RemoteSearchSession("银河旅人", SearchSource { _, _ ->
            calls.incrementAndGet(); SearchPage(listOf(hit("1")), hasMore = true)
        })
        assertTrue(session.loadNext().hasMore)
        val second = session.loadNext()
        assertEquals(1, second.hits.size)
        assertFalse(second.hasMore)
        assertEquals(2, calls.get())
    }

    @Test fun totalUsesRawRowsRatherThanFilteredHitCount() = runBlocking {
        val session = RemoteSearchSession("银河旅人", SearchSource { _, _ ->
            SearchPage(listOf(hit("1")), totalResults = 2, rawCount = 2)
        })
        assertFalse(session.loadNext().hasMore)
    }

    @Test fun directIdLookupNeverExpandsOrOffersAnotherPage() = runBlocking {
        val calls = mutableListOf<Pair<String, Int>>()
        val session = RemoteSearchSession("JM123", SearchSource { query, page ->
            calls.add(query to page); SearchPage(listOf(hit("123")), direct = true)
        })
        val result = session.loadNext()
        assertEquals(listOf("123" to 1), calls)
        assertEquals(MatchKind.ID, result.hits.single().match?.kind)
        assertFalse(result.hasMore)
    }

    @Test fun failedExpansionDoesNotDiscardSuccessfulResults() = runBlocking {
        val session = RemoteSearchSession("銀河ABC", SearchSource { query, _ ->
            if (query != "銀河ABC") throw IOException("offline fixture")
            SearchPage(listOf(hit("1", "其他故事")), hasMore = false)
        })
        val first = session.loadNext()
        assertEquals("1", first.hits.single().id)
        assertNotNull(first.notice)
        assertTrue(first.hasMore)
        assertFalse(session.loadNext().hasMore)
    }

    @Test fun failureRetriesTheSamePageAndThenRecovers() = runBlocking {
        val calls = AtomicInteger()
        val pages = mutableListOf<Int>()
        val session = RemoteSearchSession("JM123", SearchSource { _, page ->
            pages.add(page)
            if (calls.incrementAndGet() == 1) throw IOException("offline fixture")
            SearchPage(listOf(hit("123")), hasMore = false)
        })
        assertTrue(session.loadNext().hasMore)
        assertEquals("123", session.loadNext().hits.single().id)
        assertEquals(listOf(1, 1), pages)
    }

    @Test(timeout = 5000) fun cancellationReachesTheSource() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val session = RemoteSearchSession("银河", SearchSource { _, _ ->
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        })
        val work = async { session.loadNext() }
        started.await()
        work.cancelAndJoin()
        assertTrue(cancelled.isCompleted)
    }

    @Test fun childTimeoutIsAFailureRatherThanCancellingTheCaller() = runBlocking {
        val session = RemoteSearchSession("JM123", SearchSource { _, _ ->
            withTimeout(1) { awaitCancellation() }
        })
        val result = session.loadNext()
        assertTrue(result.hits.isEmpty())
        assertNotNull(result.notice)
    }

    @Test fun requestAndConcurrencyBudgetsAreRespected() = runBlocking {
        val calls = AtomicInteger()
        val running = AtomicInteger()
        val maximum = AtomicInteger()
        val session = RemoteSearchSession("数据库ABC", SearchSource { query, page ->
            val now = running.incrementAndGet()
            maximum.updateAndGet { maxOf(it, now) }
            try {
                delay(2)
                val id = calls.incrementAndGet().toString()
                SearchPage(listOf(hit(id, "其他故事$page-$id")), hasMore = true)
            } finally { running.decrementAndGet() }
        })
        val first = session.loadNext()
        assertTrue(calls.get() <= 4)
        var result = first
        repeat(30) { if (result.hasMore) result = session.loadNext() }
        assertFalse(result.hasMore)
        assertTrue(calls.get() <= 24)
        assertEquals(2, maximum.get())
        assertEquals(result.hits.size, result.hits.map { it.id }.distinct().size)
    }

    @Test fun emptyQueryDoesNotCallTheSource() = runBlocking {
        val session = RemoteSearchSession(" ", SearchSource { _, _ -> error("must not call") })
        assertFalse(session.loadNext().hasMore)
    }
}
