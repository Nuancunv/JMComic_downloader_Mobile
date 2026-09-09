@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    kotlinx.coroutines.FlowPreview::class,
)

package com.nuancun.jmcomic.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nuancun.jmcomic.mobile.MainViewModel
import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.reader.ReaderBackground
import com.nuancun.jmcomic.mobile.reader.ReaderPreferences
import com.nuancun.jmcomic.mobile.reader.ReaderSettings
import com.nuancun.jmcomic.mobile.reader.ReadingMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun PdfReaderScreen(
    album: AlbumEntity,
    initialPage: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val file = remember(album.pdfPath) { album.pdfPath?.let(::File) }
    if (file == null || !file.isFile) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val session = remember(file) { PdfRenderSession(file) }
    DisposableEffect(session) { onDispose { session.close() } }
    val preferences = remember { ReaderPreferences(context.applicationContext) }
    var settings by remember { mutableStateOf(preferences.load()) }
    var showSettings by remember { mutableStateOf(false) }
    var uiVisible by rememberSaveable(album.albumId) { mutableStateOf(true) }
    var currentPage by rememberSaveable(album.albumId, initialPage) {
        mutableIntStateOf(initialPage.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0)))
    }
    var sliderPage by rememberSaveable(album.albumId, initialPage) { mutableFloatStateOf(currentPage.toFloat()) }
    val activity = remember(context) { context.findActivity() }
    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: -1f }
    val background = Color(settings.background.argb)
    val foreground = if (settings.background == ReaderBackground.BLACK) Color.White else Color(0xFF252525)

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

    Scaffold(
        containerColor = background,
        topBar = {
            AnimatedVisibility(
                visible = uiVisible,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(190)) { -it },
            ) {
                TopAppBar(
                    title = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                    actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "阅读设置") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background, titleContentColor = foreground, navigationIconContentColor = foreground, actionIconContentColor = foreground),
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiVisible,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(190)) { it },
            ) {
                Row(
                    Modifier.fillMaxWidth().background(background).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (currentPage > 0) currentPage--; sliderPage = currentPage.toFloat() }, enabled = currentPage > 0) { Text("‹", color = foreground) }
                    Slider(
                        value = sliderPage,
                        onValueChange = { sliderPage = it },
                        onValueChangeFinished = { currentPage = sliderPage.toInt().coerceIn(0, session.pageCount - 1) },
                        valueRange = 0f..(session.pageCount - 1).coerceAtLeast(0).toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text("${currentPage + 1}/${session.pageCount}", color = foreground)
                    IconButton(onClick = { if (currentPage + 1 < session.pageCount) currentPage++; sliderPage = currentPage.toFloat() }, enabled = currentPage + 1 < session.pageCount) { Text("›", color = foreground) }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(background)
                .pointerInput(showSettings) {
                    detectTapGestures(onTap = { if (!showSettings) uiVisible = !uiVisible })
                },
        ) {
            when (settings.mode) {
                ReadingMode.HORIZONTAL -> PagedReader(session, currentPage, vertical = false) { page ->
                    currentPage = page
                    sliderPage = page.toFloat()
                    viewModel.updateReadPosition(album.albumId, page)
                }
                ReadingMode.VERTICAL -> PagedReader(session, currentPage, vertical = true) { page ->
                    currentPage = page
                    sliderPage = page.toFloat()
                    viewModel.updateReadPosition(album.albumId, page)
                }
                ReadingMode.CONTINUOUS -> ContinuousReader(session, currentPage, album.lastReadOffset, background) { page, offset ->
                    currentPage = page
                    sliderPage = page.toFloat()
                    viewModel.updateReadPosition(album.albumId, page, offset)
                }
            }
            warmthColor(settings.warmthLevel)?.let { warm ->
                Box(Modifier.fillMaxSize().background(warm))
            }
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(settings, updateSettings, onDismiss = { showSettings = false })
    }
}

@Composable
private fun PagedReader(session: PdfRenderSession, requestedPage: Int, vertical: Boolean, onPageSettled: (Int) -> Unit) {
    val state = rememberPagerState(initialPage = requestedPage, pageCount = { session.pageCount })
    LaunchedEffect(requestedPage) {
        if (state.currentPage != requestedPage) state.animateScrollToPage(requestedPage)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.distinctUntilChanged().collect(onPageSettled)
    }
    if (vertical) {
        VerticalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            PdfPage(session, page, continuous = false)
        }
    } else {
        HorizontalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            PdfPage(session, page, continuous = false)
        }
    }
}

@Composable
private fun ContinuousReader(
    session: PdfRenderSession,
    requestedPage: Int,
    initialOffset: Int,
    background: Color,
    onPositionChanged: (Int, Int) -> Unit,
) {
    val state = rememberLazyListState(requestedPage, initialOffset.coerceAtLeast(0))
    LaunchedEffect(requestedPage) {
        if (state.firstVisibleItemIndex != requestedPage) state.scrollToItem(requestedPage)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .debounce(350)
            .distinctUntilChanged()
            .collect { (page, offset) -> onPositionChanged(page, offset) }
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().background(background),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(session.pageCount, key = { it }) { page -> PdfPage(session, page, continuous = true) }
    }
}

@Composable
private fun PdfPage(session: PdfRenderSession, index: Int, continuous: Boolean) {
    var bitmap by remember(session, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(session, index, continuous) {
        val rendered = withContext(Dispatchers.IO + NonCancellable) {
            session.render(index, 1080, 4096)
        }
        if (currentCoroutineContext().isActive) {
            bitmap?.recycle()
            bitmap = rendered
        } else rendered.recycle()
    }
    DisposableEffect(session, index) { onDispose { bitmap?.recycle(); bitmap = null } }
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("翻页模式", fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("左右分页", ReadingMode.HORIZONTAL, settings, update)
                ModeChip("上下分页", ReadingMode.VERTICAL, settings, update)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("连续滚动", ReadingMode.CONTINUOUS, settings, update)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("跟随系统亮度", Modifier.weight(1f))
                Switch(settings.followSystemBrightness, { update(settings.copy(followSystemBrightness = it)) })
            }
            Slider(
                value = settings.brightness,
                onValueChange = { update(settings.copy(brightness = it, followSystemBrightness = false)) },
                valueRange = 0.05f..1f,
            )
            Text("护眼暖色", fontWeight = FontWeight.Medium)
            listOf(listOf("关闭", "轻暖"), listOf("适中", "较暖")).forEachIndexed { row, labels ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEachIndexed { column, label ->
                        val level = row * 2 + column
                    FilterChip(selected = settings.warmthLevel == level, onClick = { update(settings.copy(warmthLevel = level)) }, label = { Text(label) })
                    }
                }
            }
            Text("背景颜色", fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ReaderBackground.entries.forEach { choice ->
                    val selected = choice == settings.background
                    Box(
                        Modifier
                            .size(if (selected) 38.dp else 34.dp)
                            .clip(CircleShape)
                            .background(Color(choice.argb))
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                            .clickable { update(settings.copy(background = choice)) },
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Text("✓", color = if (choice == ReaderBackground.BLACK) Color.White else Color.Black) }
                }
            }
            Text(settings.background.label, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("完成") }
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

private class PdfRenderSession(file: File) : AutoCloseable {
    private val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
    private val mutex = Mutex()
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var closeRequested = false
    val pageCount: Int get() = renderer.pageCount

    suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap = mutex.withLock {
        val page = renderer.openPage(index)
        try {
            val scale = minOf(maxWidth.toFloat() / page.width, maxHeight.toFloat() / page.height)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            return@withLock Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        } finally {
            page.close()
        }
    }

    override fun close() {
        if (closeRequested) return
        closeRequested = true
        closeScope.launch {
            mutex.withLock { renderer.close() }
        }
    }
}
