package com.nuancun.jmcomic.mobile.source

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

/** Interceptors terminate all requests. These tests never contact a content service. */
class JmImageDownloadTest {
    @Test fun streamsBytesToDiskWithoutAndroid() = runBlocking {
        val output = File.createTempFile("download", ".fixture")
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(payload.toResponseBody("image/jpeg".toMediaType())).build()
        }.build())
        try {
            assertEquals(payload.size.toLong(), api.downloadImage("123", "1.jpg", output))
            assertArrayEquals(payload, output.readBytes())
        } finally { output.delete() }
    }

    @Test fun refusesHtmlOversizedAndTruncatedBodiesAndCleansFailedTransfer() = runBlocking {
        val output = File.createTempFile("invalid-download", ".fixture")
        try {
            for ((type, length) in listOf("text/html" to 3L, "image/jpeg" to 51L * 1024 * 1024, "image/jpeg" to 100L)) {
                val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
                    val body = object : ResponseBody() {
                        override fun contentType() = type.toMediaType()
                        override fun contentLength() = length
                        override fun source(): BufferedSource = Buffer().write(byteArrayOf(1, 2, 3))
                    }
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(body).build()
                }.build())
                try {
                    api.downloadImage("123", "1.jpg", output)
                    fail("Invalid response accepted")
                } catch (_: SourceException) { assertFalse(output.exists()) }
            }
        } finally { output.delete() }
    }

    @Test(timeout = 7000) fun cancellationWaitsForBodyWriterAndDoesNotTryAnotherMirror() = runBlocking {
        val reading = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val calls = AtomicInteger()
        val output = File.createTempFile("cancel-download", ".fixture")
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            val body = object : ResponseBody() {
                override fun contentType() = "image/jpeg".toMediaType()
                override fun contentLength() = -1L
                override fun source(): BufferedSource = object : Source {
                    override fun timeout() = Timeout.NONE
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        reading.countDown()
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                        while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(2)
                        throw IOException("cancelled fixture")
                    }
                    override fun close() { exited.countDown() }
                }.buffer()
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(body).build()
        }.build())
        try {
            val work = async { api.downloadImage("123", "1.jpg", output) }
            assertTrue(withContext(Dispatchers.IO) { reading.await(2, TimeUnit.SECONDS) })
            work.cancelAndJoin()
            assertEquals(0L, exited.count)
            assertEquals(1, calls.get())
        } finally { output.delete() }
    }
}
