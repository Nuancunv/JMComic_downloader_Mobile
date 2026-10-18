package com.nuancun.jmcomic.mobile.work

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DownloadRuntimeTest {
    @Test fun idleDiscardsUnfinishedProbeAndDoesNotCountTheGapAsNetworkTime() {
        val policy = DownloadConcurrency()
        repeat(8) { policy.record(true, 1000, (it + 1) * 1_000_000L) }
        assertEquals(5, policy.limit)
        policy.idle()
        assertEquals(4, policy.limit)
        policy.begin(10_000_000_000_000L)
        repeat(8) { policy.record(true, 1000, 10_000_000_000_000L + (it + 1) * 1_000_000L) }
        assertEquals(5, policy.limit)
    }

    @Test fun emptyNetworkQueueResetsThePolicyBetweenBooks() = runBlocking {
        var time = 1L
        val policy = DownloadConcurrency()
        val gate = AdaptiveDownloadGate(now = { time }, policy = policy)
        repeat(32) {
            gate.transfer({ _: Unit -> 1000 }) { time += 1_000_000; Unit }
            time += 10_000_000_000_000
            assertEquals(4, policy.limit)
        }
    }

    @Test fun healthyStableBandwidthMustNotDecayIntoSerialDownloads() {
        val policy = DownloadConcurrency()
        val limits = mutableListOf<Int>()
        repeat(160) {
            policy.record(true, 1000, (it + 1) * 1_000_000L)
            limits.add(policy.limit)
        }
        println("stable successful transfers: ${limits.distinct()}; final=${policy.limit}")
        assertTrue("Healthy transfers collapsed to ${policy.limit} concurrent slot(s)", policy.limit >= 4)
    }

    @Test(timeout = 5000) fun readingBookGetsNextVacantSlotAndCancelledWaitersAreRemoved() = runBlocking {
        var preferred: String? = null
        val gate = AdaptiveDownloadGate { preferred }
        val release = CompletableDeferred<Unit>()
        val holders = List(4) {
            launch(start = CoroutineStart.UNDISPATCHED) { gate.transfer({ _: Unit -> 0 }) { release.await() } }
        }
        val order = mutableListOf<String>()
        val ordinary = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.transfer({ _: Unit -> 0 }, "other") { order.add("other"); Unit }
        }
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.transfer({ _: Unit -> 0 }, "reading") { fail("Cancelled waiter was admitted") }
        }
        cancelled.cancelAndJoin()
        val reading = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.transfer({ _: Unit -> 0 }, "reading") { order.add("reading"); Unit }
        }
        preferred = "reading"
        release.complete(Unit)
        holders.forEach { it.join() }
        ordinary.join()
        reading.join()
        assertEquals(listOf("reading", "other"), order)
    }

    @Test fun healthyThroughputProbesAndCongestionReducesConcurrency() {
        val policy = DownloadConcurrency()
        repeat(16) { policy.record(true, 10_000, (it + 1) * 1_000_000L) }
        assertTrue(policy.limit > 4)
        val previous = policy.limit
        policy.record(false, 0, 20_000_000)
        assertEquals(previous / 2, policy.limit)
        repeat(20) { policy.record(false, 0, 30_000_000L + it) }
        assertEquals(1, policy.limit)
    }

    @Test fun throughputPlateauDoesNotKeepIncreasingConnections() {
        val policy = DownloadConcurrency()
        repeat(8) { policy.record(true, 1000, (it + 1) * 100L) }
        assertEquals(5, policy.limit)
        repeat(10) { policy.record(true, 1000, 1000L + it * 1000L) }
        assertEquals(4, policy.limit)
    }

    @Test(timeout = 5000) fun multipleBooksShareBudgetAndCancellationReturnsPermits() = runBlocking {
        val gate = AdaptiveDownloadGate()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val blocker = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.transfer({ _: Unit -> 0 }) { entered.complete(Unit); delay(Long.MAX_VALUE) }
        }
        entered.await()
        blocker.cancelAndJoin()
        withTimeout(3000) {
            List(32) {
                async {
                    gate.transfer({ _: Unit -> 1000 }) {
                        val count = active.incrementAndGet()
                        peak.updateAndGet { maxOf(it, count) }
                        try { delay(10) } finally { active.decrementAndGet() }
                    }
                }
            }.awaitAll()
        }
        assertTrue(peak.get() in 2..8)
        assertEquals(0, active.get())
    }

    @Test(timeout = 5000) fun albumReplacementAndDeletionWaitForWritersToExit() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = launch { DownloadRuntime.withAlbum("fixture") { entered.complete(Unit); release.await() } }
        entered.await()
        val second = async { DownloadRuntime.withAlbum("fixture") { true } }
        delay(30)
        assertFalse(second.isCompleted)
        release.complete(Unit)
        first.join()
        assertTrue(second.await())
    }
}
