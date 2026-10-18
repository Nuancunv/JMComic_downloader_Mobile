package com.nuancun.jmcomic.mobile.work

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChapterPageQueueTest {
    @Test(timeout = 5000) fun slowFirstPageDoesNotLeaveOtherDownloadSlotsIdle() = runBlocking {
        val slow = CompletableDeferred<Unit>()
        val others = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        val work = async {
            downloadChapterPages(32) { page ->
                if (page == 0) slow.await()
                seen.add(page)
                if (seen.size == 31) others.complete(Unit)
            }
        }
        others.await()
        assertEquals((1..31).toList(), seen.sorted())
        slow.complete(Unit)
        assertTrue(work.await().isEmpty())
        assertEquals((0..31).toList(), seen.sorted())
    }

    @Test fun aFailedPageLeavesAllSuccessfulSiblingsAvailableForResume() = runBlocking {
        val saved = mutableListOf<Int>()
        val failures = downloadChapterPages(12) { page ->
            if (page == 3) throw IOException("fixture")
            saved.add(page)
        }
        assertEquals(1, failures.size)
        assertEquals((0..11).filter { it != 3 }, saved.sorted())
    }

    @Test(timeout = 5000) fun cancellationJoinsEveryChildBeforeReturning() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val active = AtomicInteger()
        val work = async {
            downloadChapterPages(16) {
                active.incrementAndGet()
                entered.complete(Unit)
                try { delay(Long.MAX_VALUE) } finally { active.decrementAndGet() }
            }
        }
        entered.await()
        work.cancelAndJoin()
        assertEquals(0, active.get())
    }
}
