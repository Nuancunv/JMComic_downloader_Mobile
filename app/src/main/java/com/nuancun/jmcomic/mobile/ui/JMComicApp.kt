@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nuancun.jmcomic.mobile.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuancun.jmcomic.mobile.MainViewModel
import com.nuancun.jmcomic.mobile.reader.ReadingPosition
import kotlinx.coroutines.flow.map

private enum class MainTab(val label: String) { LIBRARY("书库"), ADD("搜索"), TASKS("任务") }
private enum class PageKind { MAIN, ARCHIVE, DETAIL, READER }
private data class AppPage(val kind: PageKind, val albumId: String? = null, val initialPage: Int = 0,
    val chapterId: String? = null, val chapterPage: Int = 0) {
    val key: String get() = "${kind.name}:${albumId.orEmpty()}"
}

@Composable
fun JMComicApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val appearancePreferences = remember(context) { AppearancePreferences(context.applicationContext) }
    var appearance by remember { mutableStateOf(appearancePreferences.load()) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    JMComicTheme(appearance) {
        var tab by rememberSaveable { mutableStateOf(MainTab.LIBRARY) }
        var detailId by rememberSaveable { mutableStateOf<String?>(null) }
        var readerAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
        var readerInitialPage by rememberSaveable { mutableIntStateOf(0) }
        var readerChapterId by rememberSaveable { mutableStateOf<String?>(null) }
        var readerChapterPage by rememberSaveable { mutableIntStateOf(0) }
        var archivePage by rememberSaveable { mutableStateOf(false) }
        val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
        val activity = remember { SceneActivity() }
        val focus = LocalFocusManager.current
        val page = when {
            readerAlbumId != null -> AppPage(PageKind.READER, readerAlbumId, readerInitialPage, readerChapterId, readerChapterPage)
            detailId != null -> AppPage(PageKind.DETAIL, detailId)
            archivePage -> AppPage(PageKind.ARCHIVE)
            else -> AppPage(PageKind.MAIN)
        }
        BackHandler(enabled = readerAlbumId != null) { readerAlbumId = null }
        BackHandler(enabled = readerAlbumId == null && detailId != null) { detailId = null }
        BackHandler(enabled = readerAlbumId == null && detailId == null && archivePage) { archivePage = false }
        BackHandler(enabled = readerAlbumId == null && detailId == null && !archivePage && tab != MainTab.LIBRARY) { tab = MainTab.LIBRARY }
        LaunchedEffect(operationMessage) {
            operationMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.consumeOperationMessage() }
        }
        LaunchedEffect(page, tab) { focus.clearFocus() }
        CompositionLocalProvider(LocalSceneActivity provides activity) {
            PauseHeavyEffects(showAppearance)
            AmbientScene(enabled = readerAlbumId == null) {
                PageTransition(page, keyOf = { it.key },
                    direction = { from, to -> if (to.kind.ordinal >= from.kind.ordinal) 1 else -1 },
                    saveState = { it.kind != PageKind.READER }) { destination ->
                    when (destination.kind) {
                        PageKind.MAIN -> MainShell(viewModel, tab, { tab = it },
                            onAppearance = { showAppearance = true }, onOpen = { detailId = it },
                            onArchive = { archivePage = true })
                        PageKind.ARCHIVE -> ArchiveRoute(viewModel, { archivePage = false }, { detailId = it })
                        PageKind.DETAIL, PageKind.READER -> AlbumRoute(viewModel, destination,
                            onBack = { if (destination.kind == PageKind.READER) readerAlbumId = null else detailId = null },
                            onRead = { requested ->
                                readerInitialPage = requested.page
                                readerChapterId = requested.chapterId
                                readerChapterPage = requested.chapterPage
                                readerAlbumId = destination.albumId
                            })
                    }
                }
                if (showAppearance) AppearanceSheet(appearance,
                    onSelect = { appearance = it; appearancePreferences.save(it) }, onDismiss = { showAppearance = false })
            }
        }
    }
}

@Composable
private fun MainShell(viewModel: MainViewModel, tab: MainTab, onTab: (MainTab) -> Unit,
    onAppearance: () -> Unit, onOpen: (String) -> Unit, onArchive: () -> Unit) {
    val backdrop = rememberChromeBackdrop()
    Scaffold(containerColor = Color.Transparent,
        topBar = { AppTopBar("JMComic", backdrop = backdrop, actions = {
            IconButton(onClick = onAppearance) { Icon(Icons.Default.Palette, "外观设置") }
        }) },
        bottomBar = { FloatingNavigation(tab, onTab, backdrop) },
    ) { padding ->
        Box(Modifier.fillMaxSize().chromeBackdrop(backdrop)) {
            Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                PageTransition(tab, keyOf = { it.name }, direction = { from, to -> if (to.ordinal > from.ordinal) 1 else -1 }) { item ->
                    when (item) {
                        MainTab.LIBRARY -> LibraryRoute(viewModel, onOpen, { onTab(MainTab.ADD) }, onArchive)
                        MainTab.ADD -> SearchScreen(viewModel) { onOpen(it.albumId) }
                        MainTab.TASKS -> TaskScreen(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNavigation(tab: MainTab, onTab: (MainTab) -> Unit, backdrop: ChromeBackdrop) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        FrostedChrome(backdrop, if (LocalWarmDark.current) MaterialTheme.colorScheme.surface else Color.White,
            Modifier.widthIn(max = 480.dp).fillMaxWidth(), radius = 32.dp) {
            Row(Modifier.fillMaxWidth().selectableGroup().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MainTab.entries.forEach { item ->
                    val selected = tab == item
                    val fill by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "navigationTint")
                    val ink = if (selected) actionInk else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(fill)
                        .selectable(selected, role = Role.Tab, onClick = { onTab(item) })
                        .heightIn(min = 56.dp).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(when (item) { MainTab.LIBRARY -> Icons.AutoMirrored.Filled.MenuBook; MainTab.ADD -> Icons.Default.Search; MainTab.TASKS -> Icons.Default.Download }, null, Modifier.size(21.dp), tint = ink)
                        Text(item.label, style = MaterialTheme.typography.labelMedium, color = ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRoute(viewModel: MainViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit, onArchive: () -> Unit) {
    val albums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
    val count by viewModel.archivedCount.collectAsStateWithLifecycle()
    val query by viewModel.localQuery.collectAsStateWithLifecycle()
    LibraryScreen(albums, query, count, { viewModel.localQuery.value = it }, { onOpen(it.albumId) }, onAdd, onArchive,
        { viewModel.setArchived(it.albumId, true) }, { viewModel.deleteAlbum(it.albumId) })
}

@Composable
private fun ArchiveRoute(viewModel: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val albums by viewModel.archivedAlbums.collectAsStateWithLifecycle()
    val query by viewModel.archiveQuery.collectAsStateWithLifecycle()
    ArchiveScreen(albums, query, { viewModel.archiveQuery.value = it }, onBack, { onOpen(it.albumId) },
        { viewModel.setArchived(it.albumId, false) }, { viewModel.deleteAlbum(it.albumId) })
}

@Composable
private fun AlbumRoute(viewModel: MainViewModel, page: AppPage, onBack: () -> Unit, onRead: (ReadingPosition) -> Unit) {
    val id = requireNotNull(page.albumId)
    val flow = remember(viewModel, id) { viewModel.observeAlbum(id).map { true to it } }
    val loaded by flow.collectAsStateWithLifecycle(initialValue = false to null)
    val album = loaded.second
    if (album == null) {
        if (loaded.first && LocalPageActive.current) LaunchedEffect(id) { onBack() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (page.kind == PageKind.READER) PdfReaderScreen(album, ReadingPosition(page.initialPage, page.chapterId, page.chapterPage), viewModel, onBack)
    else {
        val chapterFlow = remember(viewModel, id) { viewModel.observeAlbumChapters(id) }
        val chapters by chapterFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        AlbumDetailScreen(album, chapters, onBack, onRead, { viewModel.enqueue(id) }, { viewModel.exportPdf(album, chapters, it) })
    }
}
