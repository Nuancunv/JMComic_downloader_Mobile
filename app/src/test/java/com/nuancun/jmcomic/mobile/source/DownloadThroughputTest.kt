package com.nuancun.jmcomic.mobile.source

import com.nuancun.jmcomic.mobile.work.AdaptiveDownloadGate
import com.nuancun.jmcomic.mobile.work.downloadChapterPages
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertTrue
import org.junit.Test

/** Production transport and scheduling, with every response supplied by an interceptor. */
class DownloadThroughputTest {
    private data class Measurement(val millis: Long, val limited: Int, val fallback: Int)

    private suspend fun batch(parallel: Boolean, limitedHost: Boolean = true): Measurement {
        val primary = AtomicReference<String?>()
        val inFlight = AtomicInteger()
        val limited = AtomicInteger()
        val fallback = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val host = chain.request().url.host
            primary.compareAndSet(null, host)
            var code = 200
            if (host == primary.get()) {
                val concurrent = inFlight.incrementAndGet()
                try {
                    if (limitedHost && concurrent > 1) { limited.incrementAndGet(); code = 429 }
                    else Thread.sleep(15)
                } finally { inFlight.decrementAndGet() }
            } else {
                fallback.incrementAndGet()
                Thread.sleep(250)
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code)
                .message("fixture").header("Retry-After", "0")
                .body(ByteArray(8192).toResponseBody("image/jpeg".toMediaType())).build()
        }.build()
        val api = JmApiClient(client, ImageRoutes())
        val directory = kotlin.io.path.createTempDirectory("download-throughput").toFile()
        val started = System.nanoTime()
        try {
            suspend fun page(index: Int) { api.downloadImage("123", "$index.jpg", File(directory, "$index.jpg")) }
            if (parallel) {
                val gate = AdaptiveDownloadGate()
                val errors = downloadChapterPages(16) { index -> gate.transfer({ _: Unit -> 8192 }) { page(index) } }
                check(errors.isEmpty()) { errors.toString() }
            } else repeat(16) { page(it) }
            return Measurement((System.nanoTime() - started) / 1_000_000, limited.get(), fallback.get())
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test(timeout = 20000) fun enablingConcurrencyMustNotLockTheBatchOntoSlowFallbacks() = runBlocking {
        batch(false) // Warm the HTTP dispatcher before comparing timings.
        val serial = batch(false)
        val concurrent = batch(true)
        println("identical fixture: serial=$serial, concurrent=$concurrent")
        assertTrue("Throttling unnecessarily selected slow mirrors: $concurrent", concurrent.fallback == 0)
        assertTrue("Concurrent scheduling regressed: serial=$serial, concurrent=$concurrent",
            concurrent.millis <= serial.millis * 1.1)
    }

    @Test(timeout = 20000) fun healthyEndpointActuallyBenefitsFromParallelTransfers() = runBlocking {
        batch(false, false)
        val serial = batch(false, false)
        val concurrent = batch(true, false)
        println("healthy fixture: serial=$serial, concurrent=$concurrent")
        assertTrue("Healthy endpoint did not benefit: serial=$serial, concurrent=$concurrent",
            concurrent.millis < serial.millis * 0.65)
    }
}
