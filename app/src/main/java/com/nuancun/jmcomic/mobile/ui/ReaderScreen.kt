@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    kotlinx.coroutines.FlowPreview::class,
)

package com.nuancun.jmcomic.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nuancun.jmcomic.mobile.MainViewModel
import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.data.ChapterEntity
import com.nuancun.jmcomic.mobile.reader.ReadingIndex
import com.nuancun.jmcomic.mobile.reader.ReadingPosition
import com.nuancun.jmcomic.mobile.reader.ReaderBackground
import com.nuancun.jmcomic.mobile.reader.ReaderPreferences
import com.nuancun.jmcomic.mobile.reader.ReaderSettings
import com.nuancun.jmcomic.mobile.reader.ReadingMode
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun PdfReaderScreen(
    album: AlbumEntity,
    initialPage: ReadingPosition,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val active = LocalPageActive.current
    DisposableEffect(album.albumId, active) {
        val lease = if (active) viewModel.readingAlbum(album.albumId) else null
        onDispose { lease?.close() }
    }
    // Keep the source for this visit: publishing the final PDF must not interrupt reading.
    val file = remember(album.albumId) { album.pdfPath?.let(::File)?.takeIf { it.isFile } }
    val load by produceState<Pair<ReaderRenderSession?, Boolean>>(null to false, album.albumId) {
        var opened: ReaderRenderSession? = null
        try {
            var useImages: Boolean? = null
            var published: ReadingIndex? = null
            viewModel.observeReadingIndex(album.albumId).collect { index ->
                if (useImages == null) useImages = index.entries.isNotEmpty() || file == null
                if (useImages == true) {
                    if (index.entries.isNotEmpty()) {
                        published = index.retaining(published)
                        opened = ImageRenderSession(published!!)
                        value = opened to false
                    } else if (opened == null) {
                        value = null to true
                    }
                } else if (opened == null && file != null) {
                    val chapters = viewModel.observeAlbumChapters(album.albumId).first()
                    withContext(Dispatchers.IO) { opened = PdfRenderSession(file, chapters) }
                    value = opened to false
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = null to true
            awaitCancellation()
        } finally { opened?.close() }
    }
    val session = load.first
    var renderFailed by remember(album.albumId) { mutableStateOf(false) }
    if (session == null || renderFailed) {
        if (load.second || renderFailed) EmptyState("无法打开文档", "请返回详情，检查本地 PDF 是否完整。", "返回", onBack, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    val preferences = remember { ReaderPreferences(context.applicationContext) }
    var settings by remember { mutableStateOf(preferences.load()) }
    var showSettings by remember { mutableStateOf(false) }
    var uiVisible by rememberSaveable(album.albumId) { mutableStateOf(true) }
    var anchor by rememberSaveable(album.albumId, initialPage, stateSaver = listSaver<ReadingPosition, Any>(
        save = { listOf(it.page, it.chapterId.orEmpty(), it.chapterPage) },
        restore = { ReadingPosition(it[0] as Int, (it[1] as String).ifEmpty { null }, it[2] as Int) },
    )) {
        mutableStateOf(initialPage)
    }
    val currentPage = session.indexOf(anchor).coerceIn(0, session.pageCount - 1)
    var sliderPage by rememberSaveable(album.albumId, initialPage) { mutableFloatStateOf(currentPage.toFloat()) }
    LaunchedEffect(session) {
        sliderPage = currentPage.toFloat()
    }
    val savePosition: (Int, Int) -> Unit = { page, offset ->
        anchor = session.positionAt(page)
        viewModel.updateReadPosition(album.albumId, anchor, offset)
    }
    val activity = remember(context) { context.findActivity() }
    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: -1f }
    val background = Color(settings.background.argb)
    val foreground = if (settings.background == ReaderBackground.BLACK) Color(0xFFF0EEE6) else Color(0xFF302A24)

    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
            }
        }
    }
    LaunchedEffect(activity, settings.followSystemBrightness, settings.brightness) {
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply {
                screenBrightness = if (settings.followSystemBrightness) -1f else settings.brightness.coerceIn(0.05f, 1f)
            }
        }
    }
    LaunchedEffect(activity, uiVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (uiVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val updateSettings: (ReaderSettings) -> Unit = { updated ->
        settings = updated
        preferences.save(updated)
    }

    val backdrop = rememberChromeBackdrop(includeAmbient = false)
    val appUsesLightIcons = MaterialTheme.colorScheme.background.luminance() < 0.5f
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = settings.background != ReaderBackground.BLACK
                isAppearanceLightNavigationBars = settings.background != ReaderBackground.BLACK
            }
        }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !appUsesLightIcons
                    isAppearanceLightNavigationBars = !appUsesLightIcons
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(background)) {
        // Controls overlay a stable viewport, so toggling them never rescales a PDF page.
        Box(Modifier.fillMaxSize().chromeBackdrop(backdrop).windowInsetsPadding(WindowInsets.displayCutout)
            .pointerInput(showSettings) {
                detectTapGestures(onTap = { if (!showSettings) uiVisible = !uiVisible })
            }) {
            when (settings.mode) {
                ReadingMode.HORIZONTAL -> PagedReader(session, currentPage, vertical = false, onError = { renderFailed = true }) { page ->
                    sliderPage = page.toFloat()
                    savePosition(page, 0)
                }
                ReadingMode.VERTICAL -> PagedReader(session, currentPage, vertical = true, onError = { renderFailed = true }) { page ->
                    sliderPage = page.toFloat()
                    savePosition(page, 0)
                }
                ReadingMode.CONTINUOUS -> ContinuousReader(session, currentPage, album.lastReadOffset, background, onError = { renderFailed = true }) { page, offset ->
                    sliderPage = page.toFloat()
                    savePosition(page, offset)
                }
            }
            warmthColor(settings.warmthLevel)?.let { warm -> Box(Modifier.fillMaxSize().background(warm)) }
        }
        AnimatedVisibility(
            visible = uiVisible, modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(190)) { -it },
        ) {
            FrostedChrome(backdrop, background) {
                Column {
                    TopAppBar(
                        title = { Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                        actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "阅读设置") } },
                        windowInsets = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = foreground, navigationIconContentColor = foreground, actionIconContentColor = foreground),
                    )
                    HorizontalDivider(color = foreground.copy(alpha = 0.10f))
                }
            }
        }
        AnimatedVisibility(
            visible = uiVisible, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(190)) { it },
        ) {
            FrostedChrome(backdrop, background) {
                Column {
                    HorizontalDivider(color = foreground.copy(alpha = 0.10f))
                    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)))
                        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val buttonColors = IconButtonDefaults.iconButtonColors(contentColor = foreground, disabledContentColor = foreground.copy(alpha = 0.35f))
                        IconButton(onClick = { if (currentPage > 0) anchor = session.positionAt(currentPage - 1); sliderPage = session.indexOf(anchor).toFloat() }, enabled = currentPage > 0, colors = buttonColors) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一页")
                        }
                        Slider(value = sliderPage, onValueChange = { sliderPage = it },
                            onValueChangeFinished = { anchor = session.positionAt(sliderPage.toInt().coerceIn(0, session.pageCount - 1)) },
                            valueRange = 0f..(session.pageCount - 1).coerceAtLeast(0).toFloat(),
                            modifier = Modifier.weight(1f).semantics { contentDescription = "阅读进度" },
                            colors = SliderDefaults.colors(
                                thumbColor = if (settings.background == ReaderBackground.BLACK) Color(0xFFF0B39C) else Color(0xFF943E28),
                                activeTrackColor = if (settings.background == ReaderBackground.BLACK) Color(0xFFF0B39C) else Color(0xFF943E28),
                                inactiveTrackColor = foreground.copy(alpha = 0.14f),
                            ))
                        Text("${currentPage + 1} / ${session.pageCount}", color = foreground, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 6.dp))
                        IconButton(onClick = { if (currentPage + 1 < session.pageCount) anchor = session.positionAt(currentPage + 1); sliderPage = session.indexOf(anchor).toFloat() }, enabled = currentPage + 1 < session.pageCount, colors = buttonColors) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一页")
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(settings, updateSettings, onDismiss = { showSettings = false })
    }
}

@Composable
private fun PagedReader(session: ReaderRenderSession, requestedPage: Int, vertical: Boolean, onError: () -> Unit, onPageSettled: (Int) -> Unit) {
    val state = rememberPagerState(initialPage = requestedPage, pageCount = { session.pageCount })
    PauseHeavyEffects(state.isScrollInProgress)
    LaunchedEffect(requestedPage) {
        if (state.currentPage != requestedPage) state.animateScrollToPage(requestedPage)
    }
    val latestPageSettled by rememberUpdatedState(onPageSettled)
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.distinctUntilChanged().collect { latestPageSettled(it) }
    }
    if (vertical) {
        VerticalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            PdfPage(session, page, continuous = false, onError)
        }
    } else {
        HorizontalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            PdfPage(session, page, continuous = false, onError)
        }
    }
}

@Composable
private fun ContinuousReader(
    session: ReaderRenderSession,
    requestedPage: Int,
    initialOffset: Int,
    background: Color,
    onError: () -> Unit,
    onPositionChanged: (Int, Int) -> Unit,
) {
    val state = rememberLazyListState(requestedPage, initialOffset.coerceAtLeast(0))
    PauseHeavyEffects(state.isScrollInProgress)
    LaunchedEffect(requestedPage) {
        if (state.firstVisibleItemIndex != requestedPage) state.scrollToItem(requestedPage)
    }
    val latestPositionChanged by rememberUpdatedState(onPositionChanged)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .debounce(350)
            .distinctUntilChanged()
            .collect { (page, offset) -> latestPositionChanged(page, offset) }
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().background(background),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(session.pageCount, key = { session.positionAt(it).let { p -> "${p.chapterId}:${p.chapterPage}:${p.page}" } }) { page ->
            PdfPage(session, page, continuous = true, onError)
        }
    }
}

@Composable
private fun PdfPage(session: ReaderRenderSession, index: Int, continuous: Boolean, onError: () -> Unit) {
    var bitmap by remember(session, index) { mutableStateOf<Bitmap?>(null) }
    val renderWidth = LocalWindowInfo.current.containerSize.width.coerceIn(1, 1080)
    LaunchedEffect(session, index, renderWidth) {
        var pending: Bitmap? = null
        try {
            withContext(Dispatchers.IO) { pending = session.render(index, renderWidth, 4096) }
            currentCoroutineContext().ensureActive()
            bitmap = pending
            pending = null
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { onError() }
        finally { pending?.recycle() }
    }
    // Published bitmaps can still be held by RenderThread during the exit animation.
    // Drop the reference; only never-published, cancelled results are explicitly recycled.
    DisposableEffect(session, index) { onDispose { bitmap = null } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        bitmap?.let { rendered ->
            val modifier = if (continuous) {
                Modifier.fillMaxWidth().aspectRatio(rendered.width.toFloat() / rendered.height.coerceAtLeast(1))
            } else Modifier.fillMaxSize()
            Image(rendered.asImageBitmap(), "PDF 第 ${index + 1} 页", modifier, contentScale = ContentScale.Fit)
        } ?: Box(Modifier.fillMaxWidth().height(if (continuous) 520.dp else 1.dp))
    }
}

@Composable
private fun ReaderSettingsSheet(settings: ReaderSettings, update: (ReaderSettings) -> Unit, onDismiss: () -> Unit) {
    GlassBottomSheet(onDismissRequest = onDismiss, stableContent = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge)
            SectionHeading("翻页方式")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("左右分页", ReadingMode.HORIZONTAL, settings, update)
                ModeChip("上下分页", ReadingMode.VERTICAL, settings, update)
                ModeChip("连续滚动", ReadingMode.CONTINUOUS, settings, update)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeading("亮度")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("跟随系统亮度", Modifier.weight(1f))
                Switch(settings.followSystemBrightness, { update(settings.copy(followSystemBrightness = it)) })
            }
            Slider(
                value = settings.brightness,
                onValueChange = { update(settings.copy(brightness = it, followSystemBrightness = false)) },
                valueRange = 0.05f..1f,
                modifier = Modifier.semantics { contentDescription = "阅读亮度" },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeading("暖色")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("关闭", "轻暖", "适中", "较暖").forEachIndexed { level, label ->
                    FilterChip(selected = settings.warmthLevel == level, onClick = { update(settings.copy(warmthLevel = level)) }, label = { Text(label) })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeading("阅读背景")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
                ReaderBackground.entries.forEach { choice ->
                    val selected = choice == settings.background
                    Column(Modifier.width(64.dp).heightIn(min = 72.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { update(settings.copy(background = choice)) })
                        .semantics(mergeDescendants = true) { contentDescription = choice.label },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(choice.argb))
                                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Text("✓", color = if (choice == ReaderBackground.BLACK) Color.White else Color.Black) }
                        Text(choice.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            WarmTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("完成") }
        }
    }
}

@Composable
private fun ModeChip(label: String, mode: ReadingMode, settings: ReaderSettings, update: (ReaderSettings) -> Unit) {
    FilterChip(selected = settings.mode == mode, onClick = { update(settings.copy(mode = mode)) }, label = { Text(label) })
}

private fun warmthColor(level: Int): Color? = when (level) {
    1 -> Color(0x0FFFC878)
    2 -> Color(0x1FFFC878)
    3 -> Color(0x2EFFC878)
    else -> null
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private interface ReaderRenderSession : AutoCloseable {
    val pageCount: Int
    fun indexOf(position: ReadingPosition): Int
    fun positionAt(index: Int): ReadingPosition
    suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap
}

private class ImageRenderSession(private val index: ReadingIndex) : ReaderRenderSession {
    override val pageCount: Int get() = index.entries.size
    override fun indexOf(position: ReadingPosition): Int = index.indexOf(position)
    override fun positionAt(index: Int): ReadingPosition = this.index.entries[index.coerceIn(0, pageCount - 1)].position
    override suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap {
        currentCoroutineContext().ensureActive()
        val path = this.index.entries[index].path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (bounds.outWidth / sample > maxWidth * 2 || bounds.outHeight / sample > maxHeight * 2) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: error("图片无法解码")
        try {
            currentCoroutineContext().ensureActive()
            val scale = minOf(1f, maxWidth.toFloat() / decoded.width, maxHeight.toFloat() / decoded.height)
            if (scale == 1f) return decoded
            val resized = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1), true)
            if (resized !== decoded) decoded.recycle()
            return resized
        } catch (error: Throwable) { decoded.recycle(); throw error }
    }
    override fun close() = Unit
}

private class PdfRenderSession(file: File, private val chapters: List<ChapterEntity>) : ReaderRenderSession {
    private val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
    private val mutex = Mutex()
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closeRequested = AtomicBoolean(false)
    override val pageCount: Int = renderer.pageCount
    override fun indexOf(position: ReadingPosition): Int = chapters.firstOrNull { it.chapterId == position.chapterId }
        ?.pdfStartPage?.let { it - 1 + position.chapterPage } ?: position.page
    override fun positionAt(index: Int): ReadingPosition {
        val chapter = chapters.lastOrNull { (it.pdfStartPage ?: Int.MAX_VALUE) <= index + 1 }
        return ReadingPosition(index, chapter?.chapterId, chapter?.pdfStartPage?.let { index + 1 - it } ?: 0)
    }

    override suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap = mutex.withLock {
        val page = renderer.openPage(index)
        try {
            val scale = minOf(maxWidth.toFloat() / page.width, maxHeight.toFloat() / page.height)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (error: Throwable) { bitmap.recycle(); throw error }
        } finally {
            page.close()
        }
    }

    override fun close() {
        if (!closeRequested.compareAndSet(false, true)) return
        closeScope.launch {
            try { mutex.withLock { renderer.close() } }
            finally { closeScope.cancel() }
        }
    }
}
