package com.nuancun.jmcomic.mobile.source

data class RemoteAlbum(
    val id: String,
    val title: String,
    val authors: List<String>,
    val tags: List<String>,
    val description: String,
    val chapters: List<RemoteChapter>,
)

data class RemoteChapter(
    val id: String,
    val order: Int,
    val title: String,
)

data class RemotePhoto(
    val id: String,
    val albumId: String,
    val order: Int,
    val title: String,
    val imageNames: List<String>,
    val scrambleId: Int,
)

data class SearchHit(
    val id: String,
    val title: String,
    val author: String,
    val tags: List<String>,
    val authors: List<String> = listOf(author).filter(String::isNotBlank),
    val origins: List<SearchOrigin> = emptyList(),
    val match: SearchMatch? = null,
) {
    val authorLabel: String get() = authors.ifEmpty { listOf(author).filter(String::isNotBlank) }.distinct().joinToString(" · ")
}

data class SearchOrigin(val query: String, val page: Int, val position: Int)

data class SearchPage(
    val hits: List<SearchHit>,
    val hasMore: Boolean? = null,
    val totalResults: Int? = null,
    val rawCount: Int = hits.size,
    val direct: Boolean = false,
)

fun interface SearchSource {
    suspend fun searchPage(query: String, page: Int): SearchPage
}

class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)
