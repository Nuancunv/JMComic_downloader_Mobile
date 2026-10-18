@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nuancun.jmcomic.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.LibrarySearchHit
import com.nuancun.jmcomic.mobile.source.QueryAnalyzer
import com.nuancun.jmcomic.mobile.source.SearchMatch
import com.nuancun.jmcomic.mobile.source.SearchQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun LibraryScreen(
    albums: List<LibrarySearchHit>,
    query: String,
    archivedCount: Int,
    onQueryChange: (String) -> Unit,
    onOpen: (AlbumEntity) -> Unit,
    onAdd: () -> Unit,
    onArchiveOpen: () -> Unit,
    onArchive: (AlbumEntity) -> Unit,
    onDelete: (AlbumEntity) -> Unit,
) {
    val analyzed = remember(query) { QueryAnalyzer.analyze(query) }
    var archiveRevealed by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<AlbumEntity?>(null) }
    var refreshIndicatorVisible by remember { mutableStateOf(false) }
    val refreshIndicatorAlpha = remember { Animatable(0f) }
    val listState = rememberLazyListState()
    val compactHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() < 600.dp
    }

    BackHandler(enabled = LocalPageActive.current && openId != null) { openId = null }
    LaunchedEffect(refreshIndicatorVisible) {
        if (refreshIndicatorVisible) {
            refreshIndicatorAlpha.snapTo(1f)
            delay(80)
            refreshIndicatorAlpha.animateTo(0f, tween(280))
            refreshIndicatorVisible = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
            .distinctUntilChanged()
            .collect { (scrolling, first, offset) ->
                if (scrolling) openId = null
                if (first > 0 || offset > 0) archiveRevealed = false
            }
    }

    PauseHeavyEffects(listState.isScrollInProgress)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 6.dp).imePadding()) {
        if (!compactHeight) PageHeading("我的书库")
        SearchField(query, { onQueryChange(it.take(QueryAnalyzer.MAX_QUERY_LENGTH)) }, "搜索书库")
        Spacer(Modifier.height(16.dp))
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = {
                archiveRevealed = true
                refreshIndicatorVisible = true
            },
            modifier = Modifier.weight(1f),
            indicator = {
                if (refreshIndicatorVisible) {
                    CircularProgressIndicator(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(28.dp)
                            .zIndex(2f)
                            .graphicsLayer { alpha = refreshIndicatorAlpha.value },
                        strokeWidth = 3.dp,
                    )
                }
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                item(key = "archive") {
                    if (archiveRevealed && archivedCount > 0) {
                        Card(
                            onClick = onArchiveOpen,
                            modifier = Modifier.fillMaxWidth().animateContentSize(tween(220)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Archive, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("已归档图书", Modifier.padding(start = 10.dp).weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                                Text("$archivedCount", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                if (albums.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            if (query.isBlank()) "书库还是空的" else "没有匹配结果",
                            if (query.isBlank()) "添加新的作品" else "换一个标题、作者、标签、ID 或章节名",
                            if (query.isBlank()) "添加作品" else null,
                            if (query.isBlank()) onAdd else null,
                            if (compactHeight) Modifier.fillMaxWidth() else Modifier.fillParentMaxHeight().fillMaxWidth(),
                        )
                    }
                } else {
                    items(albums, key = { it.album.albumId }) { hit ->
                        val album = hit.album
                        SwipeActionRow(
                            itemKey = album.albumId,
                            isOpen = openId == album.albumId,
                            onOpenChange = { open -> openId = album.albumId.takeIf { open } },
                            actions = listOf(
                                archiveAction(false) { onArchive(album) },
                                deleteAction { deleteCandidate = album },
                            ),
                        ) { AlbumCard(album, onOpen, hit.match, analyzed) }
                    }
                }
            }
        }
    }

    deleteCandidate?.let { album ->
        DeleteAlbumDialog(album, onDismiss = { deleteCandidate = null }) {
            deleteCandidate = null
            onDelete(album)
        }
    }
}

@Composable
internal fun ArchiveScreen(
    albums: List<LibrarySearchHit>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (AlbumEntity) -> Unit,
    onRestore: (AlbumEntity) -> Unit,
    onDelete: (AlbumEntity) -> Unit,
) {
    val listState = rememberLazyListState()
    PauseHeavyEffects(listState.isScrollInProgress)
    val analyzed = remember(query) { QueryAnalyzer.analyze(query) }
    var openId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<AlbumEntity?>(null) }
    BackHandler(enabled = LocalPageActive.current && openId != null) { openId = null }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            AppTopBar(
                title = "已归档图书",
                navigation = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().padding(horizontal = 20.dp).padding(top = 16.dp).imePadding()) {
            SearchField(query, { onQueryChange(it.take(QueryAnalyzer.MAX_QUERY_LENGTH)) }, "搜索已归档图书")
            Spacer(Modifier.height(16.dp))
            if (albums.isEmpty()) EmptyState(if (query.isBlank()) "归档为空" else "没有匹配结果", "换一个标题、作者、标签、ID 或章节名", null, null, Modifier.fillMaxSize())
            else LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(albums, key = { it.album.albumId }) { hit ->
                    val album = hit.album
                    SwipeActionRow(
                        album.albumId,
                        openId == album.albumId,
                        { open -> openId = album.albumId.takeIf { open } },
                        listOf(
                            archiveAction(true) { onRestore(album) },
                            deleteAction { deleteCandidate = album },
                        ),
                    ) { AlbumCard(album, onOpen, hit.match, analyzed) }
                }
            }
        }
    }
    deleteCandidate?.let { album ->
        DeleteAlbumDialog(album, onDismiss = { deleteCandidate = null }) {
            deleteCandidate = null
            onDelete(album)
        }
    }
}

@Composable
private fun DeleteAlbumDialog(album: AlbumEntity, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除《${album.title}》？") },
        text = { Text("将删除应用内图片、PDF、章节目录和阅读进度。已导出的 PDF 不受影响。") },
        confirmButton = { WarmTextButton(onClick = onConfirm) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { WarmTextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AlbumCard(album: AlbumEntity, onOpen: (AlbumEntity) -> Unit, match: SearchMatch? = null, query: SearchQuery? = null) {
    PaperCard(
        onClick = { onOpen(album) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BookGlyph(Modifier.padding(top = 4.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("JM ${album.albumId}", style = MaterialTheme.typography.labelSmall, color = actionInk)
            Text(highlightedText(album.title, query), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(highlightedText(album.author.ifBlank { "作者未知" }, query), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (album.tags.isNotBlank()) Text(highlightedText(album.tags, query), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            match?.reason?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateLabel(if (album.status == DownloadState.COMPLETE && album.lastReadPage > 0) "上次读到第 ${album.lastReadPage + 1} 页" else statusText(album.status, album.completedChapters, album.totalChapters), album.status == DownloadState.FAILED)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
    }
}

