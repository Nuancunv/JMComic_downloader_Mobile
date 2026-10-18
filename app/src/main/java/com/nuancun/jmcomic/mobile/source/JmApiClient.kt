package com.nuancun.jmcomic.mobile.source

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class JmApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
) : SearchSource {
    private var imageRoutes = sharedImageRoutes

    internal constructor(client: OkHttpClient, imageRoutes: ImageRoutes) : this(client) {
        this.imageRoutes = imageRoutes
    }

    private data class Profile(val version: String, val tokenSecret: String, val hosts: List<String>)

    private val profiles = listOf(
        Profile("2.1.7", "185Hcomic3PAPP7R", listOf("www.cdnhjk.net", "www.cdngwc.cc", "www.cdngwc.net", "www.cdngwc.club")),
        Profile("2.0.6", "18comicAPP", listOf("www.cdnaspa.vip", "www.cdnaspa.club", "www.cdnplaystation6.vip", "www.cdnplaystation6.cc")),
    )
    private val imageHosts = listOf(
        "cdn-msp.jmapiproxy1.cc", "cdn-msp.jmapiproxy2.cc", "cdn-msp2.jmapiproxy2.cc",
        "cdn-msp3.jmapiproxy2.cc", "cdn-msp.jmapinodeudzn.net", "cdn-msp3.jmapinodeudzn.net",
    )
    @Volatile private var preferred: Pair<Profile, String>? = null
    private val cookiesByHost = ConcurrentHashMap<String, String>()
    private val searchClient = client.newBuilder().connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()

    override suspend fun searchPage(query: String, page: Int): SearchPage = withContext(Dispatchers.IO) {
        require(page > 0)
        val data = searchJson("/search", mapOf("search_query" to query, "page" to page.toString(), "main_tag" to "0", "o" to "mr", "t" to "a"))
        val redirected = data.optString("redirect_aid")
        if (redirected.isNotBlank()) {
            requireValidId(redirected)
            val album = searchJson("/album", mapOf("id" to redirected))
            val authors = stringList(album.opt("author"))
            return@withContext SearchPage(listOf(SearchHit(redirected, album.optString("name", "JM$redirected"),
                authors.firstOrNull().orEmpty(), stringList(album.opt("tags")), authors)), hasMore = false, direct = true)
        }
        val content = data.optJSONArray("content") ?: JSONArray()
        val hits = buildList {
            for (index in 0 until content.length()) {
                val item = content.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (!ID.matches(id)) continue
                val authors = stringList(item.opt("author"))
                add(SearchHit(
                    id = id,
                    title = item.optString("name", "未命名作品"),
                    author = authors.firstOrNull().orEmpty(),
                    tags = stringList(item.opt("tags")),
                    authors = authors,
                ))
            }
        }
        SearchPage(hits,
            hasMore = when (data.opt("has_more") ?: data.opt("hasMore")) {
                true, 1, "1", "true" -> true
                false, 0, "0", "false" -> false
                else -> null
            },
            totalResults = data.optString("total").toIntOrNull()?.takeIf { it >= 0 }, rawCount = content.length())
    }

    suspend fun album(id: String): RemoteAlbum {
        requireValidId(id)
        val data = apiJson("/album", mapOf("id" to id))
        val series = data.optJSONArray("series") ?: JSONArray()
        val chapters = buildList {
            for (index in 0 until series.length()) {
                val item = series.optJSONObject(index) ?: continue
                val chapterId = item.optString("id")
                if (ID.matches(chapterId)) add(RemoteChapter(chapterId, item.optInt("sort", index + 1), item.optString("name", "第 ${index + 1} 章")))
            }
            if (isEmpty()) add(RemoteChapter(id, 1, data.optString("name", "单章")))
        }.distinctBy { it.id }.sortedBy { it.order }
        return RemoteAlbum(
            id = data.optString("id", id),
            title = data.optString("name", "JM$id"),
            authors = stringList(data.opt("author")),
            tags = stringList(data.opt("tags")),
            description = data.optString("description"),
            chapters = chapters,
        )
    }

    suspend fun photo(id: String): RemotePhoto {
        requireValidId(id)
        val data = apiJson("/chapter", mapOf("id" to id))
        val albumId = data.optString("series_id").ifBlank { id }
        val series = data.optJSONArray("series") ?: JSONArray()
        var order = 1
        for (index in 0 until series.length()) {
            val item = series.optJSONObject(index) ?: continue
            if (item.optString("id") == id) order = item.optInt("sort", index + 1)
        }
        return RemotePhoto(
            id = data.optString("id", id),
            albumId = albumId,
            order = order,
            title = data.optString("name", "章节 $id"),
            imageNames = stringList(data.opt("images")),
            scrambleId = scrambleId(id),
        )
    }

    suspend fun downloadImage(photoId: String, imageName: String, destination: File): Long {
        requireValidId(photoId)
        require(IMAGE_NAME.matches(imageName)) { "图片文件名无效" }
        var last: Throwable? = null
        for (host in imageRoutes.candidates(imageHosts)) {
            for (attempt in 0..1) {
                currentCoroutineContext().ensureActive()
                val started = System.nanoTime()
                try {
                    val url = "https://$host/media/photos/$photoId/$imageName".toHttpUrl()
                    val request = Request.Builder().url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                        .header("X-Requested-With", "com.JMComic3.app")
                        .header("Referer", "https://${preferred?.second ?: profiles.first().hosts.first()}")
                        .build()
                    val size = imageRoutes.transfer(host) { downloadResponse(request) { response ->
                        if (response.code == 429) throw ImageRateLimited(response.header("Retry-After"))
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body ?: throw IOException("图片响应为空")
                        if (body.contentLength() > MAX_IMAGE_BYTES) throw IOException("图片超过 50 MB 限制")
                        val mediaType = body.contentType()
                        if (mediaType != null && mediaType.type != "image" && mediaType.toString() != "application/octet-stream") {
                            throw IOException("图片响应类型无效")
                        }
                        var count = 0L
                        FileOutputStream(destination).use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    count += read
                                    if (count > MAX_IMAGE_BYTES) throw IOException("响应超过大小限制")
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        if (count == 0L || body.contentLength() >= 0 && count != body.contentLength()) throw IOException("图片大小无效")
                        count
                    } }
                    imageRoutes.succeeded(host, size, System.nanoTime() - started)
                    return size
                } catch (error: CancellationException) {
                    throw error
                } catch (error: ImageRateLimited) {
                    destination.delete()
                    last = error
                    if (!imageRoutes.throttled(host, error.retryAfter) || attempt == 1) break
                } catch (error: ImageRouteCooling) {
                    last = error
                    break
                } catch (error: Exception) {
                    destination.delete()
                    imageRoutes.failed(host)
                    last = error
                    break
                }
            }
        }
        throw SourceException("所有图片线路均下载失败", last)
    }

    private suspend fun scrambleId(photoId: String): Int {
        return requestApi(
            "/chapter_view_template",
            mapOf("id" to photoId, "mode" to "vertical", "page" to "0", "app_img_shunt" to "1", "express" to "off", "v" to nowSeconds().toString()),
            JmCrypto.CONTENT_TOKEN_SECRET,
        ) { text, _ ->
            Regex("var\\s+scramble_id\\s*=\\s*(\\d+);").find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: throw SourceException("章节图片不完整")
        }
    }

    private suspend fun apiJson(path: String, params: Map<String, String>): JSONObject =
        requestApi(path, params, null, decode = ::decodeApiJson)

    private fun decodeApiJson(text: String, timestamp: Long): JSONObject {
        // Upstream v2.7.8 accepts whitespace followed by a UTF-8 BOM in API JSON.
        val envelope = JSONObject(text.trim().trimStart('\uFEFF').trimStart())
        if (envelope.optInt("code") != 200) throw SourceException("来源接口返回 ${envelope.optInt("code")}")
        val encrypted = envelope.opt("data") as? String
        if (encrypted.isNullOrBlank()) throw SourceException("来源接口未返回数据")
        return JSONObject(JmCrypto.decryptResponse(encrypted, timestamp))
    }

    private suspend fun <T> requestApi(
        path: String,
        params: Map<String, String>,
        overrideSecret: String?,
        transport: OkHttpClient = client,
        decode: (String, Long) -> T,
    ): T {
        val candidates = apiCandidates()
        var last: Throwable? = null
        for ((profile, host) in candidates) {
            currentCoroutineContext().ensureActive()
            try {
                ensureCookie(profile, host, transport)
                val (text, timestamp) = execute(profile, host, path, params, overrideSecret, transport)
                // A healthy HTTP response is only preferred after validation and decryption succeed.
                val result = decode(text, timestamp)
                preferred = profile to host
                return result
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { last = error }
        }
        throw SourceException("所有 API 线路均不可用", last)
    }

    private fun apiCandidates(): List<Pair<Profile, String>> =
        (listOfNotNull(preferred) + profiles.flatMap { profile -> profile.hosts.map { profile to it } }).distinct()

    private suspend fun searchJson(path: String, params: Map<String, String>): JSONObject =
        requestApi(path, params, null, searchClient, ::decodeApiJson)

    private suspend fun ensureCookie(profile: Profile, host: String, transport: OkHttpClient) {
        if (cookiesByHost.containsKey(host)) return
        try { execute(profile, host, "/setting", emptyMap(), null, transport) }
        catch (error: CancellationException) { throw error }
        catch (_: IOException) { /* Cookie bootstrap is optional. */ }
    }

    private suspend fun execute(profile: Profile, host: String, path: String, params: Map<String, String>, overrideSecret: String?, transport: OkHttpClient): Pair<String, Long> {
        val timestamp = nowSeconds()
        return downloadResponse(apiRequest(profile, host, path, params, overrideSecret, timestamp), transport) { readApiResponse(it, host) } to timestamp
    }

    private suspend fun <T> downloadResponse(request: Request, transport: OkHttpClient = client, read: (Response) -> T): T {
        currentCoroutineContext().ensureActive()
        val call = transport.newCall(request)
        val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
        return try { suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    try { if (continuation.isActive) continuation.resumeWithException(e) }
                    finally { finished.complete(Unit) }
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!continuation.isActive) return
                            val result = read(it)
                            if (continuation.isActive) continuation.resume(result)
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally { finished.complete(Unit) }
                }
            })
        } } finally {
            // A cancelled continuation must not leave a callback writing after album deletion.
            withContext(kotlinx.coroutines.NonCancellable) { finished.await() }
        }
    }

    private fun apiRequest(profile: Profile, host: String, path: String, params: Map<String, String>, overrideSecret: String?, timestamp: Long): Request {
        require(host in profiles.flatMap { it.hosts }) { "API 主机不在允许列表" }
        val url = "https://$host$path".toHttpUrl().newBuilder().apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val tokenSecret = overrideSecret ?: profile.tokenSecret
        return Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("token", JmCrypto.token(timestamp, tokenSecret))
            .header("tokenparam", "$timestamp,${profile.version}")
            .apply { cookiesByHost[host]?.let { header("Cookie", it) } }
            .build()
    }

    private fun readApiResponse(response: Response, host: String): String = response.use {
            if (response.isRedirect) throw IOException("拒绝来源重定向")
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.headers("Set-Cookie").takeIf { it.isNotEmpty() }?.let { values ->
                cookiesByHost[host] = values.map { it.substringBefore(';') }.joinToString("; ")
            }
            val body = response.body ?: throw IOException("接口响应为空")
            if (body.contentLength() > MAX_API_BYTES) throw IOException("接口响应过大")
            val bytes = readLimited(body.byteStream(), MAX_API_BYTES)
            bytes.toString(Charsets.UTF_8)
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray = input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("响应超过大小限制")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private fun stringList(value: Any?): List<String> = when (value) {
        is JSONArray -> buildList { for (index in 0 until value.length()) value.optString(index).takeIf { it.isNotBlank() }?.let(::add) }
        is String -> value.split(',', ' ').map(String::trim).filter(String::isNotBlank)
        else -> emptyList()
    }

    private fun requireValidId(id: String) = require(ID.matches(id)) { "作品或章节 ID 必须是数字" }
    private fun nowSeconds() = System.currentTimeMillis() / 1000

    companion object {
        private val sharedImageRoutes = ImageRoutes()
        private val ID = Regex("^[0-9]{1,12}$")
        private val IMAGE_NAME = Regex("^[A-Za-z0-9_-]{1,80}\\.(jpg|jpeg|png|webp|gif)$", RegexOption.IGNORE_CASE)
        private const val MAX_API_BYTES = 8L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 50L * 1024 * 1024
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
    }
}
