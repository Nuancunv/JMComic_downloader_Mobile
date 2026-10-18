package com.nuancun.jmcomic.mobile.source

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ImageRoutesTest {
    @Test fun retryAfterSupportsSecondsAndHttpDate() {
        assertEquals(0, ImageRoutes.retryDelayMillis("0"))
        assertEquals(2000, ImageRoutes.retryDelayMillis("2"))
        assertEquals(100, ImageRoutes.retryDelayMillis("invalid"))
        val epoch = 1_700_000_000_000L
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(epoch + 3000).atZone(ZoneOffset.UTC))
        assertEquals(3000, ImageRoutes.retryDelayMillis(date, epoch))
    }

    @Test fun longRetryAfterCannotBeBypassedWhenSerializationTimerExpires() = runBlocking {
        var time = 1L
        val routes = ImageRoutes { time }
        routes.throttled("a", "3600")
        time += 61_000_000_000
        var requested = false
        try { routes.transfer("a") { requested = true } }
        catch (_: ImageRouteCooling) { }
        assertFalse(requested)
    }

    @Test fun allCoolingMirrorsChooseTheEarliestRecoveryIncludingRetryAfter() {
        val routes = ImageRoutes { 1L }
        routes.throttled("a", "3600")
        routes.failed("b")
        assertEquals(listOf("b"), routes.candidates(listOf("a", "b")))
    }

    @Test(timeout = 5000) fun throttleWaitsForExistingBodiesAndCancellationRemovesWaiters() = runBlocking {
        val routes = ImageRoutes()
        val release = CompletableDeferred<Unit>()
        val first = async(start = CoroutineStart.UNDISPATCHED) { routes.transfer("a") { release.await() } }
        routes.throttled("a", "0")
        val second = async(start = CoroutineStart.UNDISPATCHED) { routes.transfer("a") { true } }
        assertFalse(second.isCompleted)
        second.cancelAndJoin()
        release.complete(Unit)
        first.await()
        assertTrue(routes.transfer("a") { true })
    }

    @Test fun failuresCoolDownAndOnlyOneMirrorIsRetriedWhenAllAreCooling() {
        var time = 1L
        val routes = ImageRoutes { time }
        val hosts = listOf("a", "b", "c")
        routes.failed("a")
        assertEquals(listOf("b", "c"), routes.candidates(hosts))
        routes.failed("b")
        routes.failed("c")
        assertEquals(1, routes.candidates(hosts).size)
        time += 11_000_000_000L
        assertEquals(hosts, routes.candidates(hosts))
    }

    @Test fun observedThroughputSelectsFastHealthyRoute() {
        val routes = ImageRoutes { 1L }
        routes.succeeded("a", 100, 100)
        routes.succeeded("b", 500, 100)
        assertEquals(listOf("b", "a", "c"), routes.candidates(listOf("a", "b", "c")))
        routes.failed("b")
        assertEquals("a", routes.candidates(listOf("a", "b", "c")).first())
    }
}
