package com.nuancun.jmcomic.mobile.data

import androidx.room.Embedded
import androidx.room.Relation
import com.nuancun.jmcomic.mobile.source.MatchKind
import com.nuancun.jmcomic.mobile.source.QueryAnalyzer
import com.nuancun.jmcomic.mobile.source.SearchDocument
import com.nuancun.jmcomic.mobile.source.SearchMatch
import com.nuancun.jmcomic.mobile.source.SearchRanker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

data class SearchableAlbum(
    @Embedded val album: AlbumEntity,
    @Relation(parentColumn = "albumId", entityColumn = "albumId", entity = ChapterEntity::class, projection = ["title"])
    val chapterTitles: List<String>,
)

data class LibrarySearchHit(val album: AlbumEntity, val match: SearchMatch)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LocalSearchRepository(private val entries: (Boolean) -> Flow<List<SearchableAlbum>>) {
    fun observe(query: Flow<String>, archived: Boolean): Flow<List<LibrarySearchHit>> =
        combine(query.debounce(120).distinctUntilChanged(), entries(archived)) { text, albums -> text to albums }
            .mapLatest { (text, albums) ->
                currentCoroutineContext().ensureActive()
                search(text, albums)
            }.flowOn(Dispatchers.Default)

    companion object {
        fun search(input: String, entries: List<SearchableAlbum>): List<LibrarySearchHit> {
            val query = QueryAnalyzer.analyze(input)
            return entries.map { entry ->
                val album = entry.album
                LibrarySearchHit(album, SearchRanker.match(query,
                    SearchDocument(album.albumId, album.title, album.author, listOf(album.tags), entry.chapterTitles)))
            }.filter { query.isEmpty || it.match.kind != MatchKind.NONE }
                .sortedWith { a, b ->
                    val relevance = SearchRanker.matchOrder.compare(a.match, b.match)
                    if (relevance != 0) relevance else b.album.updatedAt.compareTo(a.album.updatedAt)
                }
        }
    }
}
