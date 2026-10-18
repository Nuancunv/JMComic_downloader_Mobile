package com.nuancun.jmcomic.mobile.work

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** One budget for the process, including downloads belonging to different books. */
internal object DownloadRuntime {
    private val albums = ConcurrentHashMap<String, Mutex>()
    private val images = Semaphore(1)
    private val pdfs = Semaphore(1)
    private val readers = linkedMapOf<Any, String>()
    val network = AdaptiveDownloadGate { synchronized(readers) { readers.values.lastOrNull() } }

    fun reading(albumId: String): AutoCloseable {
        val token = Any()
        synchronized(readers) { readers[token] = albumId }
        return AutoCloseable { synchronized(readers) { readers.remove(token) }; Unit }
    }

    suspend fun <T> withAlbum(id: String, action: suspend () -> T): T =
        albums.getOrPut(id) { Mutex() }.withLock { action() }

    suspend fun <T> processImage(action: suspend () -> T): T = images.withPermit { action() }
    suspend fun <T> buildPdf(action: suspend () -> T): T = pdfs.withPermit { action() }
}

/** Small additive probes; congestion halves the budget. Metrics use whole-window throughput. */
internal class DownloadConcurrency(private val maximum: Int = 8) {
    var limit = minOf(4, maximum)
        private set
    private var completed = 0
    private var bytes = 0L
    private var started: Long? = null
    private var baselineRate = 0.0
    private var probing = false
    private var holdWindows = 0

    fun begin(now: Long) {
        if (started == null) started = now
    }

    fun idle() {
        // An unfinished probe must not become the next book's new baseline.
        if (probing) limit = maxOf(1, limit - 1)
        probing = false
        holdWindows = 0
        baselineRate = 0.0
        completed = 0
        bytes = 0
        started = null
    }

    fun record(success: Boolean, byteCount: Long, now: Long) {
        if (!success) {
            limit = maxOf(1, limit / 2)
            completed = 0
            bytes = 0
            started = now
            baselineRate = 0.0
            probing = false
            holdWindows = 0
            return
        }
        begin(now)
        bytes += byteCount
        val start = checkNotNull(started)
        if (++completed < limit * 2 || now <= start) return
        val rate = bytes.toDouble() / (now - start)
        if (probing && rate < baselineRate * 1.05) {
            // Roll back just the extra slot being tested, then HOLD the healthy baseline.
            limit = maxOf(1, limit - 1)
            probing = false
            holdWindows = 3
        } else {
            probing = false
            if (holdWindows > 0) holdWindows--
            else if (limit < maximum) {
                limit++
                probing = true
            }
        }
        baselineRate = rate
        completed = 0
        bytes = 0
        started = now
    }
}

internal class AdaptiveDownloadGate(
    private val now: () -> Long = System::nanoTime,
    private val policy: DownloadConcurrency = DownloadConcurrency(),
    private val preferredAlbum: () -> String? = { null },
) {
    private data class Waiting(val albumId: String?, val ready: CompletableDeferred<Unit> = CompletableDeferred())
    private val mutex = Mutex()
    private var active = 0
    private val waiting = mutableListOf<Waiting>()

    private fun admit() {
        while (active < policy.limit && waiting.isNotEmpty()) {
            val preferred = preferredAlbum()
            val index = if (preferred == null) 0 else waiting.indexOfFirst { it.albumId == preferred }.coerceAtLeast(0)
            val next = waiting.removeAt(index)
            if (active == 0) policy.begin(now())
            active++
            next.ready.complete(Unit)
        }
    }

    suspend fun <T> transfer(bytes: (T) -> Long, albumId: String? = null, action: suspend () -> T): T {
        val request = Waiting(albumId)
        mutex.withLock { waiting.add(request); admit() }
        try { request.ready.await() }
        catch (error: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock {
                    if (!waiting.remove(request)) active--
                    if (active == 0 && waiting.isEmpty()) policy.idle()
                    admit()
                }
            }
            throw error
        }
        var success = false
        var size = 0L
        var cancelled = false
        try {
            val result = action()
            size = bytes(result)
            success = true
            return result
        } catch (error: kotlinx.coroutines.CancellationException) {
            cancelled = true
            throw error
        } finally {
            // Permit release must survive cancellation of a Worker.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock {
                    active--
                    if (!cancelled) policy.record(success, size, now())
                    if (active == 0 && waiting.isEmpty()) policy.idle()
                    admit()
                }
            }
        }
    }
}
