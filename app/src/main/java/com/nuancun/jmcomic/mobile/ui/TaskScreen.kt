@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nuancun.jmcomic.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuancun.jmcomic.mobile.MainViewModel
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.DownloadTaskEntity
import com.nuancun.jmcomic.mobile.data.ExportState
import com.nuancun.jmcomic.mobile.data.ExportTaskEntity

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
internal fun TaskScreen(viewModel: MainViewModel) {
    val listState = rememberLazyListState()
    PauseHeavyEffects(listState.isScrollInProgress)
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val exportTasks by viewModel.exportTasks.collectAsStateWithLifecycle()
    val entries = remember(tasks, exportTasks) {
        (tasks.map(UnifiedTaskItem::Download) + exportTasks.map(UnifiedTaskItem::Export))
            .sortedByDescending { it.updatedAt }
    }
    val sections = remember(entries) {
        val (running, finished) = entries.partition { entry ->
            when (entry) {
                is UnifiedTaskItem.Download -> entry.value.state !in TERMINAL_TASK_STATES
                is UnifiedTaskItem.Export -> entry.value.state !in TERMINAL_EXPORT_STATES
            }
        }
        listOf("进行中" to running, "已结束" to finished).filter { it.second.isNotEmpty() }
    }
    var openId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    var deleteExportCandidate by remember { mutableStateOf<ExportTaskEntity?>(null) }
    var detailExportId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = LocalPageActive.current && openId != null) { openId = null }
    if (entries.isEmpty()) Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PageHeading("传输与导出")
        EmptyState("暂无任务", "搜索并下载作品后，可在这里查看进度", null, null, Modifier.weight(1f).fillMaxWidth())
    }
    else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp)) {
        item(key = "page-heading") { PageHeading("传输与导出") }
        sections.forEach { (title, sectionEntries) ->
            item(key = "section:$title") { SectionHeading(title, detail = "${sectionEntries.size} 项") }
            items(sectionEntries, key = { it.key }) { entry ->
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
    }
    deleteCandidate?.let { task ->
        GlassAlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("删除任务记录？") },
            text = { Text("只删除任务列表中的这条记录，漫画作品、图片和 PDF 都会保留。") },
            confirmButton = { WarmTextButton(onClick = { deleteCandidate = null; viewModel.deleteTaskRecord(task.taskId) }) { Text("删除记录", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { WarmTextButton(onClick = { deleteCandidate = null }) { Text("取消") } },
        )
    }
    deleteExportCandidate?.let { task ->
        GlassAlertDialog(
            onDismissRequest = { deleteExportCandidate = null },
            title = { Text("删除任务记录？") },
            text = { Text("只删除任务列表中的这条记录，已经导出的 PDF 文件会保留。") },
            confirmButton = { WarmTextButton(onClick = { deleteExportCandidate = null; viewModel.deleteExportTaskRecord(task.taskId) }) { Text("删除记录", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { WarmTextButton(onClick = { deleteExportCandidate = null }) { Text("取消") } },
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
    PaperCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.UploadFile, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(task.savedName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(task.message, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (task.state !in TERMINAL_EXPORT_STATES) {
                    IconButton(onClick = cancel) { Icon(Icons.Default.Cancel, "取消任务") }
                }
            }
            if (task.sourceBytes > 0L && task.state != ExportState.COMPLETE) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { task.completedBytes.toFloat() / task.sourceBytes.coerceAtLeast(1L) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateLabel("PDF 导出")
                StateLabel("· ${exportStateLabel(task.state)}", task.state == ExportState.FAILED)
            }
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
        val blurred = rememberDialogBlur()
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val limit = maxHeight * if (expanded) 0.70f else 0.50f
            GlassPanel(
                Modifier
                    .fillMaxWidth(0.90f)
                    .heightIn(max = limit)
                    .clickable { },
                blurred = blurred,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("导出详情", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭详情") }
                    }
                    val contentModifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
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
                    WarmTextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, maxLines = maxLines, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TaskCard(task: DownloadTaskEntity, cancel: () -> Unit) {
    PaperCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, null, Modifier.padding(end = 12.dp).size(22.dp), tint = actionInk)
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(task.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (task.state !in TERMINAL_TASK_STATES) {
                    IconButton(onClick = cancel) { Icon(Icons.Default.Cancel, "取消任务") }
                }
            }
            if (task.totalItems > 0 && task.state != DownloadState.COMPLETE) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { task.completedItems.toFloat() / task.totalItems.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateLabel("作品下载")
                StateLabel("· ${stateLabel(task.state)}", task.state == DownloadState.FAILED)
            }
        }
    }
}

