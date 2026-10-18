@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nuancun.jmcomic.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuancun.jmcomic.mobile.MainViewModel
import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.DownloadTaskEntity
import com.nuancun.jmcomic.mobile.source.QueryAnalyzer
import com.nuancun.jmcomic.mobile.source.SearchHit
import com.nuancun.jmcomic.mobile.source.SearchQuery
import java.io.File

@Composable
internal fun SearchScreen(viewModel: MainViewModel, onOpen: (AlbumEntity) -> Unit) {
    val query by viewModel.searchInput.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val busy by viewModel.searchBusy.collectAsStateWithLifecycle()
    val canLoadMore by viewModel.searchCanLoadMore.collectAsStateWithLifecycle()
    val error by viewModel.searchError.collectAsStateWithLifecycle()
    val applied by viewModel.searchQuery.collectAsStateWithLifecycle()
    val suggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var lastScrolledQuery by rememberSaveable { mutableStateOf(applied?.text) }
    LaunchedEffect(applied?.text) {
        if (lastScrolledQuery != applied?.text) {
            listState.scrollToItem(0)
            lastScrolledQuery = applied?.text
        }
    }
    val submit = { viewModel.search(query) }
    PauseHeavyEffects(listState.isScrollInProgress)
    val albumIndex = remember(albums) { FirstByIdIndex(albums) { it.albumId } }
    val taskIndex = remember(tasks) { FirstByIdIndex(tasks) { it.albumId } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 6.dp).imePadding()) {
        val compact = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() < 600.dp }
        if (!compact) PageHeading("发现下一本")
        SearchField(query, { viewModel.searchInput.value = it.take(QueryAnalyzer.MAX_QUERY_LENGTH) }, "ID、链接或关键词", onSearch = submit)
        if (suggestions.isNotEmpty() && !busy && applied?.text != query.trim()) {
            SectionHeading("书库中的书名")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { title ->
                    AssistChip(onClick = { viewModel.searchInput.value = title; viewModel.search(title) },
                        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (busy && results.isEmpty()) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("正在搜索", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(if (results.isEmpty()) "搜索暂未完成" else "部分结果未能加载", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    if (results.isEmpty() || canLoadMore) {
                        WarmTextButton(onClick = if (results.isEmpty()) submit else viewModel::loadMoreSearchResults, enabled = !busy) { Text("重试") }
                    } else {
                        WarmTextButton(onClick = submit, enabled = !busy) { Text("重新搜索") }
                    }
                }
            }
        }
        if (applied != null) SectionHeading("搜索结果", detail = "已加载 ${results.size} 项")
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (applied == null && !busy && error == null) {
                item(key = "search-start") { EmptyState("找到想读的作品", "输入作品 ID、链接或关键词", null, null, Modifier.fillParentMaxSize()) }
            }
            if (applied != null && !busy && results.isEmpty() && error == null) {
                item(key = "empty-search") { EmptyState("没有匹配结果", "试试较短的书名或作者", "重试", submit, Modifier.fillMaxWidth()) }
            }
            items(results, key = { it.id }) { hit ->
                val album = albumIndex[hit.id]
                val task = taskIndex[hit.id]
                SearchCard(
                    hit = hit,
                    query = applied,
                    album = album,
                    task = task,
                    enqueue = { viewModel.enqueue(hit.id) },
                    open = { album?.let(onOpen) },
                )
            }
            if (canLoadMore) {
                item(key = "load-more") {
                    WarmOutlinedButton(viewModel::loadMoreSearchResults, Modifier.fillMaxWidth(), enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp)) else Text(if (results.isEmpty()) "重试加载" else "加载更多")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCard(
    hit: SearchHit,
    query: SearchQuery?,
    album: AlbumEntity?,
    task: DownloadTaskEntity?,
    enqueue: () -> Unit,
    open: () -> Unit,
) {
    PaperCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("JM ${hit.id}", style = MaterialTheme.typography.labelSmall, color = actionInk)
                Text(highlightedText(hit.title, query), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(highlightedText(hit.authorLabel.ifBlank { "作者未知" }, query), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hit.tags.isNotEmpty()) Text(highlightedText(hit.tags.take(4).joinToString(" · "), query), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                hit.match?.reason?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            DownloadAction(album, task, enqueue, open)
        }
    }
}

@Composable
private fun DownloadAction(
    album: AlbumEntity?,
    task: DownloadTaskEntity?,
    enqueue: () -> Unit,
    open: () -> Unit,
) {
    val progressColor = actionInk
    val progressTrack = MaterialTheme.colorScheme.primaryContainer
    val largeText = LocalDensity.current.fontScale > 1.3f
    val actionWidth = if (largeText) 80.dp else 64.dp
    val complete = album?.status == DownloadState.COMPLETE && album.pdfPath?.let(::File)?.isFile == true
    val active = task?.state in setOf(
        DownloadState.QUEUED,
        DownloadState.FETCHING,
        DownloadState.DOWNLOADING,
        DownloadState.BUILDING_PDF,
    )
    Box(Modifier.width(actionWidth).heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
        when {
            complete -> ProgressRing(
                progress = 1f,
                center = { Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = progressColor) },
                onClick = open,
                actionLabel = "打开",
                description = "打开作品详情",
            )
            active -> {
                val determinate = when {
                    task?.state == DownloadState.BUILDING_PDF -> 1f
                    task != null && task.totalItems > 0 -> task.completedItems.toFloat() / task.totalItems
                    else -> null
                }
                if (determinate == null) {
                    CircularProgressIndicator(
                        Modifier.size(if (largeText) 56.dp else 44.dp).semantics { contentDescription = "正在准备下载" },
                        color = progressColor,
                        trackColor = progressTrack,
                        strokeWidth = 3.dp,
                    )
                } else {
                    ProgressRing(
                        progress = determinate,
                        center = {
                            Text(
                                if (task?.state == DownloadState.BUILDING_PDF) "PDF" else "${(determinate.coerceIn(0f, 1f) * 100).toInt()}%",
                                color = progressColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        description = if (task?.state == DownloadState.BUILDING_PDF) "正在生成 PDF" else "下载进度",
                    )
                }
            }
            task?.state == DownloadState.FAILED || task?.state == DownloadState.CANCELLED -> ProgressRing(
                progress = 0f,
                center = { Icon(Icons.Default.Refresh, null, Modifier.size(20.dp), tint = progressColor) },
                onClick = enqueue,
                actionLabel = "重试",
                description = "重新下载",
            )
            else -> Button(
                onClick = enqueue,
                modifier = Modifier.width(actionWidth).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("下载", maxLines = 1) }
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    center: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    description: String = "下载进度",
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(280), label = "downloadProgress")
    val stroke = with(LocalDensity.current) { 3.dp.toPx() }
    val progressColor = actionInk
    val progressTrack = MaterialTheme.colorScheme.primaryContainer
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(
        Modifier
            .width(if (largeText) 80.dp else 64.dp).heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = description, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (onClick == null) progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(if (largeText) 56.dp else 44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(
                    color = progressTrack,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (animated > 0f) drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = animated * 360f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            center()
        }
        actionLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = progressColor) }
    }
}

