@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nuancun.jmcomic.mobile.ui

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuancun.jmcomic.mobile.MainViewModel
import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.data.ChapterEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.DownloadTaskEntity
import com.nuancun.jmcomic.mobile.data.ExportState
import com.nuancun.jmcomic.mobile.data.ExportTaskEntity
import com.nuancun.jmcomic.mobile.source.SearchHit
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private enum class MainTab(val label: String) { LIBRARY("书库"), ADD("添加"), TASKS("任务") }
private sealed interface UnifiedTaskItem {
    val key: String
    val updatedAt: Long

    data class Download(val value: DownloadTaskEntity) : UnifiedTaskItem {
        override val key = "download:${value.taskId}"
        override val updatedAt = value.updatedAt
    }

    data class Export(val value: ExportTaskEntity) : UnifiedTaskItem {
        override val key = "export:${value.taskId}"
        override val updatedAt = value.updatedAt
    }
}

@Composable
fun JMComicApp(viewModel: MainViewModel) {
    val colors = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF65558F),
        secondary = androidx.compose.ui.graphics.Color(0xFF625B71),
    )
    MaterialTheme(colorScheme = colors) {
        var tab by rememberSaveable { mutableStateOf(MainTab.LIBRARY) }
        var detailId by rememberSaveable { mutableStateOf<String?>(null) }
        var readerAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
        var readerInitialPage by rememberSaveable { mutableStateOf(0) }
        var archivePage by rememberSaveable { mutableStateOf(false) }
        val albums by viewModel.albums.collectAsStateWithLifecycle()
        val libraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
        val archivedAlbums by viewModel.archivedAlbums.collectAsStateWithLifecycle()
        val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()
        val localQuery by viewModel.localQuery.collectAsStateWithLifecycle()
        val archiveQuery by viewModel.archiveQuery.collectAsStateWithLifecycle()
        val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val detail = detailId?.let { id -> albums.firstOrNull { it.albumId == id } }

        BackHandler(enabled = readerAlbumId != null) { readerAlbumId = null }
        BackHandler(enabled = readerAlbumId == null && detailId != null) {
            detailId = null
            viewModel.selectAlbum(null)
        }
        BackHandler(enabled = readerAlbumId == null && detailId == null && archivePage) { archivePage = false }
        BackHandler(enabled = readerAlbumId == null && detailId == null && !archivePage && tab != MainTab.LIBRARY) {
            tab = MainTab.LIBRARY
        }

        LaunchedEffect(operationMessage) {
            operationMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.consumeOperationMessage()
            }
        }

        when {
            readerAlbumId != null && detail != null -> {
                PdfReaderScreen(detail, readerInitialPage, viewModel, onBack = { readerAlbumId = null })
            }
            detail != null -> {
                LaunchedEffect(detail.albumId) { viewModel.selectAlbum(detail.albumId) }
                val chapters by viewModel.chapters.collectAsStateWithLifecycle()
                AlbumDetailScreen(
                    detail,
                    chapters,
                    onBack = { detailId = null; viewModel.selectAlbum(null) },
                    onReadAt = { page -> readerInitialPage = page; readerAlbumId = detail.albumId },
                    onRetry = { viewModel.enqueue(detail.albumId) },
                    onExport = { uri -> viewModel.exportPdf(detail, chapters, uri) },
                )
            }
            archivePage -> ArchiveScreen(
                albums = archivedAlbums,
                query = archiveQuery,
                onQueryChange = { viewModel.archiveQuery.value = it },
                onBack = { archivePage = false },
                onOpen = { detailId = it.albumId },
                onRestore = { viewModel.setArchived(it.albumId, false) },
                onDelete = { viewModel.deleteAlbum(it.albumId) },
            )
            else -> Scaffold(
                topBar = { TopAppBar(title = { Text("JMComic 本地书库") }) },
                bottomBar = {
                    NavigationBar {
                        MainTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(when (item) { MainTab.LIBRARY -> Icons.AutoMirrored.Filled.MenuBook; MainTab.ADD -> Icons.Default.Add; MainTab.TASKS -> Icons.Default.Download }, null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (tab) {
                        MainTab.LIBRARY -> LibraryScreen(
                            libraryAlbums,
                            localQuery,
                            archivedCount,
                            onQueryChange = { viewModel.localQuery.value = it },
                            onOpen = { detailId = it.albumId },
                            onAdd = { tab = MainTab.ADD },
                            onArchiveOpen = { archivePage = true },
                            onArchive = { viewModel.setArchived(it.albumId, true) },
                            onDelete = { viewModel.deleteAlbum(it.albumId) },
                        )
                        MainTab.ADD -> AddScreen(viewModel) { album -> detailId = album.albumId }
                        MainTab.TASKS -> TaskScreen(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    albums: List<AlbumEntity>,
    query: String,
    archivedCount: Int,
    onQueryChange: (String) -> Unit,
    onOpen: (AlbumEntity) -> Unit,
    onAdd: () -> Unit,
    onArchiveOpen: () -> Unit,
    onArchive: (AlbumEntity) -> Unit,
    onDelete: (AlbumEntity) -> Unit,
) {
    var archiveRevealed by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<AlbumEntity?>(null) }
    var refreshIndicatorVisible by remember { mutableStateOf(false) }
    val refreshIndicatorAlpha = remember { Animatable(0f) }
    val listState = rememberLazyListState()
    val compactHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() < 600.dp
    }

    BackHandler(enabled = openId != null) { openId = null }
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            query,
            onQueryChange,
            Modifier.fillMaxWidth(),
            label = { Text("搜索书库", maxLines = 1) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "archive") {
                    if (archiveRevealed && archivedCount > 0) {
                        Card(
                            onClick = onArchiveOpen,
                            modifier = Modifier.fillMaxWidth().animateContentSize(tween(220)),
                            colors = CardDefaults.cardColors(containerColor = ArchiveActionColors.first),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Archive, null, tint = ArchiveActionColors.second)
                                Text("已归档图书", Modifier.padding(start = 10.dp).weight(1f), color = ArchiveActionColors.second, fontWeight = FontWeight.SemiBold)
                                Text("$archivedCount", color = ArchiveActionColors.second)
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
                    items(albums, key = { it.albumId }) { album ->
                        SwipeActionRow(
                            itemKey = album.albumId,
                            isOpen = openId == album.albumId,
                            onOpenChange = { open -> openId = album.albumId.takeIf { open } },
                            actions = listOf(
                                archiveAction(false) { onArchive(album) },
                                deleteAction { deleteCandidate = album },
                            ),
                        ) { AlbumCard(album, onOpen) }
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
private fun ArchiveScreen(
    albums: List<AlbumEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (AlbumEntity) -> Unit,
    onRestore: (AlbumEntity) -> Unit,
    onDelete: (AlbumEntity) -> Unit,
) {
    var openId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<AlbumEntity?>(null) }
    BackHandler(enabled = openId != null) { openId = null }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已归档图书") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth(), label = { Text("检索归档") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            if (albums.isEmpty()) EmptyState("归档为空", "归档的作品会显示在这里", null, null, Modifier.fillMaxSize())
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(albums, key = { it.albumId }) { album ->
                    SwipeActionRow(
                        album.albumId,
                        openId == album.albumId,
                        { open -> openId = album.albumId.takeIf { open } },
                        listOf(
                            archiveAction(true) { onRestore(album) },
                            deleteAction { deleteCandidate = album },
                        ),
                    ) { AlbumCard(album, onOpen) }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除《${album.title}》？") },
        text = { Text("将删除应用内图片、PDF、章节目录和阅读进度。已导出的 PDF 不受影响。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除", color = DeleteActionColors.second) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AlbumCard(album: AlbumEntity, onOpen: (AlbumEntity) -> Unit) {
    Card(
        onClick = { onOpen(album) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(album.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text("JM${album.albumId} · ${album.author}", style = MaterialTheme.typography.bodySmall)
            if (album.tags.isNotBlank()) Text(album.tags, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Text(statusText(album.status, album.completedChapters, album.totalChapters), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AddScreen(viewModel: MainViewModel, onOpen: (AlbumEntity) -> Unit) {
    val query by viewModel.searchInput.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsState()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val busy by viewModel.searchBusy.collectAsState()
    val canLoadMore by viewModel.searchCanLoadMore.collectAsState()
    val error by viewModel.searchError.collectAsState()
    val submit = { viewModel.search(query) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            query, { viewModel.searchInput.value = it }, Modifier.fillMaxWidth(),
            label = { Text("搜索 ID、链接或关键词", maxLines = 1) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { IconButton(onClick = submit) { Icon(Icons.Default.Search, "在线检索") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }), singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        if (busy && results.isEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        Text("在线结果", style = MaterialTheme.typography.labelLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.id }) { hit ->
                val album = albums.firstOrNull { it.albumId == hit.id }
                val task = tasks.firstOrNull { it.albumId == hit.id }
                SearchCard(
                    hit = hit,
                    album = album,
                    task = task,
                    enqueue = { viewModel.enqueue(hit.id) },
                    open = { album?.let(onOpen) },
                )
            }
            if (results.isNotEmpty() && canLoadMore) {
                item(key = "load-more") {
                    OutlinedButton(viewModel::loadMoreSearchResults, Modifier.fillMaxWidth(), enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp)) else Text("加载更多")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCard(
    hit: SearchHit,
    album: AlbumEntity?,
    task: DownloadTaskEntity?,
    enqueue: () -> Unit,
    open: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(hit.title, fontWeight = FontWeight.SemiBold)
                Text("JM${hit.id}${hit.author.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                if (hit.tags.isNotEmpty()) Text(hit.tags.take(4).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
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
    val complete = album?.status == DownloadState.COMPLETE && album.pdfPath?.let(::File)?.isFile == true
    val active = task?.state in setOf(
        DownloadState.QUEUED,
        DownloadState.FETCHING,
        DownloadState.DOWNLOADING,
        DownloadState.BUILDING_PDF,
    )
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        when {
            complete -> ProgressRing(
                progress = 1f,
                center = { Icon(Icons.Default.Check, "打开作品", tint = ProgressDeepColor) },
                onClick = open,
            )
            active -> {
                val determinate = when {
                    task?.state == DownloadState.BUILDING_PDF -> 1f
                    task != null && task.totalItems > 0 -> task.completedItems.toFloat() / task.totalItems
                    else -> null
                }
                if (determinate == null) {
                    CircularProgressIndicator(
                        Modifier.size(72.dp),
                        color = ProgressDeepColor,
                        trackColor = ProgressLightColor,
                        strokeWidth = 6.dp,
                    )
                } else {
                    ProgressRing(
                        progress = determinate,
                        center = {
                            Text(
                                if (task?.state == DownloadState.BUILDING_PDF) "PDF" else "${(determinate.coerceIn(0f, 1f) * 100).toInt()}%",
                                color = ProgressDeepColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                    )
                }
            }
            task?.state == DownloadState.FAILED || task?.state == DownloadState.CANCELLED -> ProgressRing(
                progress = 0f,
                center = { Icon(Icons.Default.Refresh, "重新下载", tint = ProgressDeepColor) },
                onClick = enqueue,
            )
            else -> Button(
                onClick = enqueue,
                modifier = Modifier.width(72.dp).height(48.dp),
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
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(280), label = "downloadProgress")
    val stroke = with(LocalDensity.current) { 6.dp.toPx() }
    Box(
        Modifier
            .size(72.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                color = ProgressLightColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) drawArc(
                color = ProgressDeepColor,
                startAngle = -90f,
                sweepAngle = animated * 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        center()
    }
}

@Composable
private fun TaskScreen(viewModel: MainViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val exportTasks by viewModel.exportTasks.collectAsStateWithLifecycle()
    val entries = remember(tasks, exportTasks) {
        (tasks.map(UnifiedTaskItem::Download) + exportTasks.map(UnifiedTaskItem::Export))
            .sortedByDescending { it.updatedAt }
    }
    var openId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    var deleteExportCandidate by remember { mutableStateOf<ExportTaskEntity?>(null) }
    var detailExportId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = openId != null) { openId = null }
    if (entries.isEmpty()) EmptyState("暂无任务", "从“添加”页面检索并下载作品", null, null, Modifier.fillMaxSize())
    else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is UnifiedTaskItem.Download -> {
                    val task = entry.value
                    if (task.state in TERMINAL_TASK_STATES) {
                        SwipeActionRow(
                            entry.key,
                            openId == entry.key,
                            { open -> openId = entry.key.takeIf { open } },
                            listOf(deleteAction("删除记录") { deleteCandidate = task }),
                        ) { TaskCard(task) { viewModel.cancel(task.taskId) } }
                    } else TaskCard(task) { viewModel.cancel(task.taskId) }
                }
                is UnifiedTaskItem.Export -> {
                    val task = entry.value
                    val card = @Composable {
                        ExportTaskCard(
                            task = task,
                            onClick = { detailExportId = task.taskId },
                            cancel = { viewModel.cancelExport(task.taskId) },
                        )
                    }
                    if (task.state in TERMINAL_EXPORT_STATES) {
                        SwipeActionRow(
                            entry.key,
                            openId == entry.key,
                            { open -> openId = entry.key.takeIf { open } },
                            listOf(deleteAction("删除记录") { deleteExportCandidate = task }),
                            content = card,
                        )
                    } else card()
                }
            }
        }
    }
    deleteCandidate?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("删除任务记录？") },
            text = { Text("只删除任务列表中的这条记录，漫画作品、图片和 PDF 都会保留。") },
            confirmButton = { TextButton(onClick = { deleteCandidate = null; viewModel.deleteTaskRecord(task.taskId) }) { Text("删除记录", color = DeleteActionColors.second) } },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("取消") } },
        )
    }
    deleteExportCandidate?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteExportCandidate = null },
            title = { Text("删除任务记录？") },
            text = { Text("只删除任务列表中的这条记录，已经导出的 PDF 文件会保留。") },
            confirmButton = { TextButton(onClick = { deleteExportCandidate = null; viewModel.deleteExportTaskRecord(task.taskId) }) { Text("删除记录", color = DeleteActionColors.second) } },
            dismissButton = { TextButton(onClick = { deleteExportCandidate = null }) { Text("取消") } },
        )
    }
    detailExportId?.let { id ->
        exportTasks.firstOrNull { it.taskId == id }?.let { task ->
            ExportTaskDetailDialog(task, onDismiss = { detailExportId = null })
        }
    }
}

@Composable
private fun ExportTaskCard(task: ExportTaskEntity, onClick: () -> Unit, cancel: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.UploadFile, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(task.savedName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(task.message, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (task.state !in TERMINAL_EXPORT_STATES) {
                    IconButton(onClick = cancel) { Icon(Icons.Default.Cancel, "取消任务") }
                }
            }
            if (task.sourceBytes > 0L) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { task.completedBytes.toFloat() / task.sourceBytes.coerceAtLeast(1L) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(exportStateLabel(task.state), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ExportTaskDetailDialog(task: ExportTaskEntity, onDismiss: () -> Unit) {
    var expanded by rememberSaveable(task.taskId) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.10f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val limit = maxHeight * if (expanded) 0.70f else 0.50f
            Card(
                Modifier
                    .fillMaxWidth(0.90f)
                    .heightIn(max = limit)
                    .clickable { },
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("导出详情", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭详情") }
                    }
                    val contentModifier = if (expanded) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier
                    Column(contentModifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        DetailRow("原作品名称", task.originalTitle, if (expanded) Int.MAX_VALUE else 1)
                        DetailRow("保存名称", task.savedName, if (expanded) Int.MAX_VALUE else 1)
                        DetailRow("保存位置", task.destinationLabel, if (expanded) Int.MAX_VALUE else 1)
                        Text("${exportStateLabel(task.state)} · ${formatTaskTime(task.createdAt)}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        if (expanded) {
                            DetailRow("作者", task.author, Int.MAX_VALUE)
                            DetailRow("标签", task.tags, Int.MAX_VALUE)
                            DetailRow("作品信息", task.description, Int.MAX_VALUE)
                            DetailRow("文档地址", task.destinationUri, Int.MAX_VALUE)
                        }
                        Text("${task.chapterCount} 章 · ${task.pageCount} 页 · ${formatBytes(task.sourceBytes)}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                        Text(if (expanded) "收起" else "详情")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, maxLines: Int) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value.ifBlank { "—" }, maxLines = maxLines, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TaskCard(task: DownloadTaskEntity, cancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.SemiBold)
                    Text(task.message, style = MaterialTheme.typography.bodySmall)
                }
                if (task.state !in TERMINAL_TASK_STATES) {
                    IconButton(onClick = cancel) { Icon(Icons.Default.Cancel, "取消任务") }
                }
            }
            if (task.totalItems > 0) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { task.completedItems.toFloat() / task.totalItems.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(stateLabel(task.state), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AlbumDetailScreen(
    album: AlbumEntity,
    chapters: List<ChapterEntity>,
    onBack: () -> Unit,
    onReadAt: (Int) -> Unit,
    onRetry: () -> Unit,
    onExport: (Uri) -> Unit,
) {
    val pdf = album.pdfPath?.let(::File)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && pdf?.isFile == true) onExport(uri)
    }
    Scaffold(topBar = { TopAppBar(title = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("JM${album.albumId} · ${album.author}", style = MaterialTheme.typography.bodyMedium)
                if (album.tags.isNotBlank()) Text(album.tags, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onReadAt(album.lastReadPage) },
                            enabled = pdf?.isFile == true,
                            modifier = Modifier.weight(1f),
                        ) { Icon(Icons.Default.Book, null); Text("阅读") }
                        OutlinedButton(
                            onClick = { exporter.launch("${safeExportName(album.title)}.pdf") },
                            enabled = pdf?.isFile == true,
                            modifier = Modifier.weight(1f),
                        ) { Text("导出 PDF") }
                    }
                    if (album.status == DownloadState.FAILED || album.status == DownloadState.CANCELLED) {
                        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("继续下载") }
                    }
                }
            }
            item { Text("章节目录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(chapters, key = { it.chapterId }) { chapter ->
                Card(
                    onClick = { onReadAt((chapter.pdfStartPage!! - 1).coerceAtLeast(0)) },
                    enabled = pdf?.isFile == true && chapter.pdfStartPage != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp).fillMaxWidth()) {
                        Text("${chapter.chapterOrder}. ${chapter.title}", Modifier.weight(1f))
                        Text(chapter.pdfStartPage?.let { "第 $it 页" } ?: chapter.status, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    action: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val compact = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() < 600.dp
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = if (compact) 4.dp else 32.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(if (compact) 42.dp else 56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = if (compact) 4.dp else 8.dp))
            if (action != null && onClick != null) Button(onClick = onClick) { Text(action) }
        }
    }
}

private val TERMINAL_TASK_STATES = setOf(DownloadState.COMPLETE, DownloadState.FAILED, DownloadState.CANCELLED)
private val TERMINAL_EXPORT_STATES = setOf(ExportState.COMPLETE, ExportState.FAILED, ExportState.CANCELLED)
private val ProgressDeepColor = Color(0xFF65558F)
private val ProgressLightColor = Color(0xFFE8DEF4)

private fun statusText(status: String, completed: Int, total: Int): String = when (status) {
    DownloadState.COMPLETE -> "整部完整 · $total 章"
    DownloadState.FAILED -> "需要重试 · $completed/$total 章"
    DownloadState.CANCELLED -> "已取消 · $completed/$total 章"
    else -> "下载中 · $completed/$total 章"
}

private fun stateLabel(state: String): String = when (state) {
    DownloadState.QUEUED -> "等待开始"
    DownloadState.FETCHING -> "读取目录"
    DownloadState.DOWNLOADING -> "下载图片"
    DownloadState.BUILDING_PDF -> "生成 PDF"
    DownloadState.COMPLETE -> "已完成"
    DownloadState.FAILED -> "失败"
    DownloadState.CANCELLED -> "已取消"
    else -> state
}

private fun exportStateLabel(state: String): String = when (state) {
    ExportState.QUEUED -> "等待导出"
    ExportState.EXPORTING -> "导出 PDF"
    ExportState.COMPLETE -> "已完成"
    ExportState.FAILED -> "失败"
    ExportState.CANCELLED -> "已取消"
    else -> state
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTaskTime(time: Long): String = java.text.DateFormat
    .getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
    .format(java.util.Date(time))

private fun safeExportName(title: String): String = title.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").take(60).ifBlank { "JMComic" }
