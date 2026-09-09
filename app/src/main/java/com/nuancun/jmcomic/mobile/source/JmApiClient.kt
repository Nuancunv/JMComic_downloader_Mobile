package com.nuancun.jmcomic.mobile.source

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class JmApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
) {
    private data class Profile(val version: String, val tokenSecret: String, val hosts: List<String>)

    private val profiles = listOf(
        Profile("2.1.2", "185Hcomic3PAPP7R", listOf("www.cdnhjk.net", "www.cdngwc.cc", "www.cdngwc.net", "www.cdngwc.club")),
        Profile("2.0.6", "18comicAPP", listOf("www.cdnaspa.vip", "www.cdnaspa.club", "www.cdnplaystation6.vip", "www.cdnplaystation6.cc")),
    )
    private val imageHosts = listOf(
        "cdn-msp.jmapiproxy1.cc", "cdn-msp.jmapiproxy2.cc", "cdn-msp2.jmapiproxy2.cc",
        "cdn-msp3.jmapiproxy2.cc", "cdn-msp.jmapinodeudzn.net", "cdn-msp3.jmapinodeudzn.net",
    )
    @Volatile private var preferred: Pair<Profile, String>? = null
    private val cookiesByHost = ConcurrentHashMap<String, String>()

    fun search(query: String, page: Int = 1): List<SearchHit> {
        val data = apiJson("/search", mapOf("search_query" to query, "page" to page.toString(), "main_tag" to "0", "o" to "mr", "t" to "a"))
        val redirected = data.optString("redirect_aid")
        if (redirected.isNotBlank()) {
            val album = album(redirected)
            return listOf(SearchHit(album.id, album.title, album.authors.firstOrNull().orEmpty(), album.tags))
        }
        val content = data.optJSONArray("content") ?: JSONArray()
        return buildList {
            for (index in 0 until content.length()) {
                val item = content.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (!ID.matches(id)) continue
                add(SearchHit(
                    id = id,
                    title = item.optString("name", "未命名作品"),
                    author = stringList(item.opt("author")).firstOrNull().orEmpty(),
                    tags = stringList(item.opt("tags")),
                ))
            }
        }
    }

    fun album(id: String): RemoteAlbum {
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

    fun photo(id: String): RemotePhoto {
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

    fun downloadImage(photoId: String, imageName: String): ByteArray {
        requireValidId(photoId)
        require(IMAGE_NAME.matches(imageName)) { "图片文件名无效" }
        var last: Throwable? = null
        for (host in imageHosts) {
            try {
                val url = "https://$host/media/photos/$photoId/$imageName".toHttpUrl()
                val request = Request.Builder().url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .header("X-Requested-With", "com.JMComic3.app")
                    .header("Referer", "https://${preferred?.second ?: profiles.first().hosts.first()}")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("图片响应为空")
                    if (body.contentLength() > MAX_IMAGE_BYTES) throw IOException("图片超过 50 MB 限制")
                    val mediaType = body.contentType()
                    if (mediaType != null && mediaType.type != "image" && mediaType.toString() != "application/octet-stream") {
                        throw IOException("图片响应类型无效")
                    }
                    val bytes = readLimited(body.byteStream(), MAX_IMAGE_BYTES)
                    if (bytes.isEmpty()) throw IOException("图片大小无效")
                    return bytes
                }
            } catch (error: Throwable) { last = error }
        }
        throw SourceException("所有图片线路均下载失败", last)
    }

    private fun scrambleId(photoId: String): Int {
        val text = apiText(
            "/chapter_view_template",
            mapOf("id" to photoId, "mode" to "vertical", "page" to "0", "app_img_shunt" to "1", "express" to "off", "v" to nowSeconds().toString()),
            JmCrypto.CONTENT_TOKEN_SECRET,
        )
        return Regex("var\\s+scramble_id\\s*=\\s*(\\d+);").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 220980
    }

    private fun apiJson(path: String, params: Map<String, String>): JSONObject {
        val (text, timestamp) = requestApi(path, params, null)
        val envelope = JSONObject(text)
        if (envelope.optInt("code") != 200) throw SourceException("来源接口返回 ${envelope.optInt("code")}")
        val encrypted = envelope.optString("data")
        if (encrypted.isBlank()) throw SourceException("来源接口未返回数据")
        return JSONObject(JmCrypto.decryptResponse(encrypted, timestamp))
    }

    private fun apiText(path: String, params: Map<String, String>, secret: String): String = requestApi(path, params, secret).first

    private fun requestApi(path: String, params: Map<String, String>, overrideSecret: String?): Pair<String, Long> {
        val candidates = preferred?.let { listOf(it) } ?: profiles.flatMap { profile -> profile.hosts.map { profile to it } }
        var last: Throwable? = null
        for ((profile, host) in candidates) {
            try {
                ensureCookie(profile, host)
                return execute(profile, host, path, params, overrideSecret)
                    .also { preferred = profile to host }
            } catch (error: Throwable) { last = error }
        }
        throw SourceException("所有 API 线路均不可用", last)
    }

    private fun ensureCookie(profile: Profile, host: String) {
        if (cookiesByHost.containsKey(host)) return
        runCatching { execute(profile, host, "/setting", emptyMap(), null) }
    }

    private fun execute(profile: Profile, host: String, path: String, params: Map<String, String>, overrideSecret: String?): Pair<String, Long> {
        require(host in profiles.flatMap { it.hosts }) { "API 主机不在允许列表" }
        val url = "https://$host$path".toHttpUrl().newBuilder().apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val timestamp = nowSeconds()
        val tokenSecret = overrideSecret ?: profile.tokenSecret
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("token", JmCrypto.token(timestamp, tokenSecret))
            .header("tokenparam", "$timestamp,${profile.version}")
            .apply { cookiesByHost[host]?.let { header("Cookie", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isRedirect) throw IOException("拒绝来源重定向")
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.headers("Set-Cookie").takeIf { it.isNotEmpty() }?.let { values ->
                cookiesByHost[host] = values.map { it.substringBefore(';') }.joinToString("; ")
            }
            val body = response.body ?: throw IOException("接口响应为空")
            if (body.contentLength() > MAX_API_BYTES) throw IOException("接口响应过大")
            val bytes = readLimited(body.byteStream(), MAX_API_BYTES)
            return bytes.toString(Charsets.UTF_8) to timestamp
        }
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
        private val ID = Regex("^[0-9]{1,12}$")
        private val IMAGE_NAME = Regex("^[A-Za-z0-9_-]{1,80}\\.(jpg|jpeg|png|webp|gif)$", RegexOption.IGNORE_CASE)
        private const val MAX_API_BYTES = 8L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 50L * 1024 * 1024
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
    }
}
