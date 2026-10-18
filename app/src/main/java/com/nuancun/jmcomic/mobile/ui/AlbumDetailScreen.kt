@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nuancun.jmcomic.mobile.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.data.ChapterEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.reader.ReadingPosition
import java.io.File

@Composable
internal fun AlbumDetailScreen(
    album: AlbumEntity,
    chapters: List<ChapterEntity>,
    onBack: () -> Unit,
    onReadAt: (ReadingPosition) -> Unit,
    onRetry: () -> Unit,
    onExport: (Uri) -> Unit,
) {
    val listState = rememberLazyListState()
    PauseHeavyEffects(listState.isScrollInProgress)
    val pdf = album.pdfPath?.let(::File)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && pdf?.isFile == true) onExport(uri)
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { AppTopBar("作品详情", navigation = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 20.dp), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            item {
                PaperCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BookGlyph()
                    Column {
                        Text("藏书档案", style = MaterialTheme.typography.labelSmall, color = actionInk)
                        Text("JM ${album.albumId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(album.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(album.author.ifBlank { "作者未知" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (album.tags.isNotBlank()) Text(album.tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onReadAt(ReadingPosition(album.lastReadPage, album.lastReadChapterId, album.lastReadChapterPage)) },
                        enabled = pdf?.isFile == true || chapters.any { it.status == DownloadState.COMPLETE && it.pageCount > 0 },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    ) {
                        Icon(Icons.Default.Book, null, Modifier.size(20.dp))
                        Text(if (album.lastReadPage > 0) "继续阅读 · 第 ${album.lastReadPage + 1} 页" else "阅读", Modifier.padding(start = 8.dp))
                    }
                    WarmTextButton(
                        onClick = { exporter.launch("${safeExportName(album.title)}.pdf") },
                        enabled = pdf?.isFile == true,
                        modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
                    ) { Text("导出 PDF") }
                    if (album.status == DownloadState.FAILED || album.status == DownloadState.CANCELLED) {
                        WarmOutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("继续下载") }
                    }
                }
                }
                }
            }
            if (album.description.isNotBlank()) item {
                SectionHeading("关于这部作品")
                Text(album.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { SectionHeading("章节目录", detail = "${chapters.size} 章") }
            items(chapters, key = { it.chapterId }) { chapter ->
                PaperCard(
                    onClick = { onReadAt(ReadingPosition((chapter.pdfStartPage?.minus(1) ?: 0).coerceAtLeast(0), chapter.chapterId)) },
                    enabled = (pdf?.isFile == true && chapter.pdfStartPage != null) ||
                        (chapter.status == DownloadState.COMPLETE && chapter.pageCount > 0),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(chapter.chapterOrder.toString().padStart(2, '0'), style = MaterialTheme.typography.titleLarge, color = actionInk)
                        Column(Modifier.weight(1f)) {
                            Text(chapter.title, style = MaterialTheme.typography.titleSmall)
                            Text(chapter.pdfStartPage?.let { "第 $it 页" } ?: stateLabel(chapter.status), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun safeExportName(title: String): String = title.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").take(60).ifBlank { "JMComic" }
