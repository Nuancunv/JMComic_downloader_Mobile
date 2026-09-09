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
)

class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)
