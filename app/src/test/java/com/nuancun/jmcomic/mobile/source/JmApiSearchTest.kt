package com.nuancun.jmcomic.mobile.source

import java.io.IOException
import java.util.Base64
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Every HTTP request ends in an interceptor; no network or Android runtime is used. */
class JmApiSearchTest {
    private fun response(chain: Interceptor.Chain, data: String, code: Int = 200): Response {
        val timestamp = chain.request().header("tokenparam")!!.substringBefore(',').toLong()
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(JmCrypto.md5Hex("$timestamp${JmCrypto.DATA_SECRET}").toByteArray(), "AES"))
        val encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(data.toByteArray()))
        return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .header("Set-Cookie", "fixture=1; Path=/")
            .body("""{"code":200,"data":"$encrypted"}""".toResponseBody("application/json".toMediaType())).build()
    }

    @Test fun preservesAllAuthorsAndExplicitPagingMetadata() = runBlocking {
        val client = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            response(chain, """{"content":[{"id":"123","name":"银河旅人","author":["甲","乙"],"tags":["旅行"]},{"id":"bad","name":"invalid"}],"total":"2","has_more":false}""")
        }.build())
        val result = client.searchPage("银河", 1)
        assertEquals(listOf("甲", "乙"), result.hits.single().authors)
        assertEquals("甲 · 乙", result.hits.single().authorLabel)
        assertEquals(2, result.rawCount)
        assertEquals(2, result.totalResults)
        assertEquals(false, result.hasMore)
    }

    @Test fun redirectResultIsTerminalAndAlbumLookupIsCancellableTransport() = runBlocking {
        val paths = Collections.synchronizedList(mutableListOf<String>())
        val client = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            paths.add(path)
            response(chain, if (path == "/album") """{"id":"123","name":"银河旅人","author":["甲","乙"]}"""
                else """{"redirect_aid":"123"}""")
        }.build())
        val result = client.searchPage("123", 1)
        assertTrue(result.direct)
        assertEquals(false, result.hasMore)
        assertEquals("123", result.hits.single().id)
        assertTrue(paths.contains("/album"))
    }

    @Test fun preferredHostFailureFallsBackToAnotherAllowedHost() = runBlocking {
        val failPreferred = AtomicBoolean(false)
        val searchHosts = Collections.synchronizedList(mutableListOf<String>())
        var preferred = ""
        val client = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath == "/search") {
                val host = chain.request().url.host
                searchHosts.add(host)
                if (failPreferred.get() && host == preferred) return@addInterceptor response(chain, "{}", 503)
            }
            response(chain, """{"content":[{"id":"123","name":"银河旅人"}],"has_more":false}""")
        }.build())
        client.searchPage("银河", 1)
        preferred = searchHosts.single()
        failPreferred.set(true)
        assertEquals("123", client.searchPage("旅人", 1).hits.single().id)
        assertEquals(preferred, searchHosts[1])
        assertNotEquals(preferred, searchHosts.last())
    }

    @Test(timeout = 7000) fun cancellingSearchCancelsTheOkHttpCallWithoutRetryingMirrors() = runBlocking {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val paths = Collections.synchronizedList(mutableListOf<String>())
        val client = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            paths.add(path)
            if (path == "/search") {
                started.countDown()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
                while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(2)
                if (chain.call().isCanceled()) cancelled.countDown()
                throw IOException("cancelled fixture")
            }
            response(chain, "{}")
        }.build())
        val work = async { client.searchPage("银河", 1) }
        assertTrue(withContext(Dispatchers.IO) { started.await(3, TimeUnit.SECONDS) })
        work.cancelAndJoin()
        assertTrue(withContext(Dispatchers.IO) { cancelled.await(2, TimeUnit.SECONDS) })
        assertEquals(1, paths.count { it == "/search" })
    }
}
