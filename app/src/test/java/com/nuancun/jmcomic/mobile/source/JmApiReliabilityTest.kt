package com.nuancun.jmcomic.mobile.source

import java.io.IOException
import java.util.Base64
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

/** All requests terminate in interceptors, including cookie and template requests. */
class JmApiReliabilityTest {
    private fun response(chain: Interceptor.Chain, text: String): Response = Response.Builder()
        .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
        .header("Set-Cookie", "fixture=1; Path=/")
        .body(text.toResponseBody("application/json".toMediaType())).build()

    private fun encrypted(chain: Interceptor.Chain, data: String): Response {
        val timestamp = chain.request().header("tokenparam")!!.substringBefore(',').toLong()
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(JmCrypto.md5Hex("$timestamp${JmCrypto.DATA_SECRET}").toByteArray(), "AES"))
        val encoded = Base64.getEncoder().encodeToString(cipher.doFinal(data.toByteArray()))
        return response(chain, """{"code":200,"data":"$encoded"}""")
    }

    @Test fun albumRetriesBusinessErrorOnAnotherHost() = runBlocking {
        assertAlbumFallback("""{"code":503,"data":""}""")
    }

    @Test fun albumRetriesMalformedEnvelopeOnAnotherHost() = runBlocking {
        assertAlbumFallback("<html>temporary gateway failure</html>")
    }

    @Test fun albumRetriesInvalidCiphertextOnAnotherHost() = runBlocking {
        assertAlbumFallback("""{"code":200,"data":"AAAA"}""")
    }

    @Test fun albumRetriesEmptyDataOnAnotherHost() = runBlocking {
        assertAlbumFallback("""{"code":200,"data":[]}""")
    }

    @Test fun templateErrorTextRetriesInsteadOfSilentlyUsingDefaultScrambleId() = runBlocking {
        val templateHosts = mutableListOf<String>()
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            when (chain.request().url.encodedPath) {
                "/chapter" -> encrypted(chain, """{"id":"123","name":"fixture","images":["001.jpg"]}""")
                "/chapter_view_template" -> {
                    templateHosts.add(chain.request().url.host)
                    val timestamp = chain.request().header("tokenparam")!!.substringBefore(',').toLong()
                    assertEquals(JmCrypto.token(timestamp, JmCrypto.CONTENT_TOKEN_SECRET), chain.request().header("token"))
                    response(chain, if (templateHosts.size == 1) "Could not connect to server" else "var scramble_id = 220981;")
                }
                else -> response(chain, "{}")
            }
        }.build())
        assertEquals(220981, api.photo("123").scrambleId)
        assertEquals(2, templateHosts.size)
        assertNotEquals(templateHosts[0], templateHosts[1])
    }

    @Test fun couldTextInEncryptedDataUsesMetadataFallback() = runBlocking {
        var calls = 0
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath == "/album") {
                encrypted(chain, if (++calls == 1) "Could not connect to server" else """{"id":"123","name":"fixture"}""")
            } else response(chain, "{}")
        }.build())
        assertEquals("fixture", api.album("123").title)
        assertEquals(2, calls)
    }

    private suspend fun assertAlbumFallback(badEnvelope: String) {
        val hosts = mutableListOf<String>()
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath == "/album") {
                hosts.add(chain.request().url.host)
                if (hosts.size == 1) response(chain, badEnvelope)
                else encrypted(chain, """{"id":"123","name":"fixture"}""")
            } else response(chain, "{}")
        }.build())
        assertEquals("fixture", api.album("123").title)
        assertEquals(2, hosts.size)
        assertNotEquals(hosts[0], hosts[1])
        api.album("123")
        assertEquals(hosts[1], hosts[2])
    }

    @Test fun albumAcceptsBomAndWhitespaceWithoutChangingHost() = runBlocking {
        val hosts = mutableListOf<String>()
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath != "/album") return@addInterceptor response(chain, "{}")
            hosts.add(chain.request().url.host)
            val valid = encrypted(chain, """{"id":"123","name":"fixture"}""")
            val text = valid.body!!.string()
            valid.close()
            response(chain, " \r\n\uFEFF$text\n ")
        }.build())
        assertEquals("fixture", api.album("123").title)
        assertEquals(1, hosts.size)
    }

    @Test fun searchUsesTheSameBomCompatibleParser() = runBlocking {
        val hosts = mutableListOf<String>()
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath != "/search") return@addInterceptor response(chain, "{}")
            hosts.add(chain.request().url.host)
            val valid = encrypted(chain, """{"content":[{"id":"123","name":"fixture"}]}""")
            val text = valid.body!!.string()
            valid.close()
            response(chain, "\uFEFF$text")
        }.build())
        assertEquals("123", api.searchPage("fixture", 1).hits.single().id)
        assertEquals(1, hosts.size)
    }

    @Test fun currentProfileUsesUpstreamVersionAndMatchingToken() = runBlocking {
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            val tokenParam = chain.request().header("tokenparam")!!
            assertEquals("2.1.7", tokenParam.substringAfter(','))
            assertEquals(JmCrypto.token(tokenParam.substringBefore(',').toLong(), "185Hcomic3PAPP7R"), chain.request().header("token"))
            encrypted(chain, """{"id":"123","name":"fixture"}""")
        }.build())
        assertEquals("123", api.album("123").id)
    }

    @Test fun chapterRetriesInvalidDecryptedJsonOnAnotherHost() = runBlocking {
        val hosts = mutableListOf<String>()
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            when (chain.request().url.encodedPath) {
                "/chapter" -> {
                    hosts.add(chain.request().url.host)
                    encrypted(chain, if (hosts.size == 1) "not-json" else """{"id":"123","images":["001.jpg"]}""")
                }
                "/chapter_view_template" -> response(chain, "var scramble_id = 220981;")
                else -> response(chain, "{}")
            }
        }.build())
        val photo = api.photo("123")
        assertEquals(listOf("001.jpg"), photo.imageNames)
        assertEquals(220981, photo.scrambleId)
        assertEquals(2, hosts.size)
    }

    @Test fun exhaustedInvalidResponsesKeepLastCauseAndBoundAttempts() = runBlocking {
        val hosts = mutableListOf<String>()
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath == "/album") hosts.add(chain.request().url.host)
            response(chain, """{"code":503}""")
        }.build())
        try {
            api.album("123")
            fail("Expected all hosts to fail")
        } catch (error: SourceException) {
            assertNotNull(error.cause)
            assertEquals(8, hosts.size)
            assertEquals(hosts.distinct(), hosts)
        }
    }

    @Test(timeout = 7000) fun cancellingMetadataDoesNotTryOtherHosts() = runBlocking {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val hosts = Collections.synchronizedList(mutableListOf<String>())
        val api = JmApiClient(OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath != "/album") return@addInterceptor response(chain, "{}")
            hosts.add(chain.request().url.host)
            started.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(2)
            if (chain.call().isCanceled()) cancelled.countDown()
            throw IOException("cancelled fixture")
        }.build())
        val work = async { api.album("123") }
        assertTrue(withContext(Dispatchers.IO) { started.await(3, TimeUnit.SECONDS) })
        work.cancelAndJoin()
        assertTrue(cancelled.await(1, TimeUnit.SECONDS))
        assertEquals(1, hosts.size)
    }
}
