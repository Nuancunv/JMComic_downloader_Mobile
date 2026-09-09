package com.nuancun.jmcomic.mobile

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.data.AppDatabase
import com.nuancun.jmcomic.mobile.data.ChapterEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.DownloadTaskEntity
import com.nuancun.jmcomic.mobile.data.ExportState
import com.nuancun.jmcomic.mobile.data.ExportTaskEntity
import com.nuancun.jmcomic.mobile.source.JmApiClient
import com.nuancun.jmcomic.mobile.source.SearchHit
import com.nuancun.jmcomic.mobile.source.SearchRanker
import com.nuancun.jmcomic.mobile.work.DownloadScheduler
import com.nuancun.jmcomic.mobile.work.ExportScheduler
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).libraryDao()
    private val api = JmApiClient()
    private var searchGeneration = 0
    private var activeSearchQuery = ""
    private var activeSearchPage = 0

    val albums: StateFlow<List<AlbumEntity>> = dao.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val localQuery = MutableStateFlow("")
    val archiveQuery = MutableStateFlow("")
    val libraryAlbums: StateFlow<List<AlbumEntity>> = localQuery
        .flatMapLatest { dao.observeAlbumsMatching(it.trim(), false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val archivedAlbums: StateFlow<List<AlbumEntity>> = archiveQuery
        .flatMapLatest { dao.observeAlbumsMatching(it.trim(), true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val archivedCount: StateFlow<Int> = dao.observeArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val tasks: StateFlow<List<DownloadTaskEntity>> = dao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exportTasks: StateFlow<List<ExportTaskEntity>> = dao.observeExportTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedAlbumId = MutableStateFlow<String?>(null)
    val chapters: StateFlow<List<ChapterEntity>> = selectedAlbumId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeChapters(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchResults = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchInput = MutableStateFlow("")
    val searchBusy = MutableStateFlow(false)
    val searchCanLoadMore = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)
    val operationMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.pendingDeletionAlbums().forEach { album ->
                runCatching {
                    WorkManager.getInstance(getApplication<Application>())
                        .cancelAllWorkByTag("album:${album.albumId}")
                        .result
                        .get()
                    finishAlbumDeletion(album.albumId)
                }.onFailure { error ->
                    dao.setPendingDeletion(album.albumId, false)
                    operationMessage.value = "上次删除未完成：${error.message ?: "请重新删除作品"}"
                }
            }
        }
    }

    fun search(query: String) {
        val normalized = normalizeSearchQuery(query)
        if (normalized.isEmpty()) return
        val generation = ++searchGeneration
        activeSearchQuery = normalized
        activeSearchPage = 1
        searchResults.value = emptyList()
        searchCanLoadMore.value = false
        executeSearch(normalized, 1, generation, append = false)
    }

    fun loadMoreSearchResults() {
        if (searchBusy.value || !searchCanLoadMore.value || activeSearchQuery.isBlank()) return
        val generation = searchGeneration
        executeSearch(activeSearchQuery, activeSearchPage + 1, generation, append = true)
    }

    private fun executeSearch(query: String, page: Int, generation: Int, append: Boolean) {
        viewModelScope.launch {
            searchBusy.value = true
            searchError.value = null
            runCatching { withContext(Dispatchers.IO) { api.search(query, page) } }
                .onSuccess { incoming ->
                    if (generation != searchGeneration) return@onSuccess
                    val merged = if (append) (searchResults.value + incoming).distinctBy { it.id } else incoming
                    searchResults.value = SearchRanker.rank(query, merged)
                    activeSearchPage = page
                    searchCanLoadMore.value = incoming.isNotEmpty()
                }
                .onFailure { error ->
                    if (generation == searchGeneration) searchError.value = error.message ?: "检索失败"
                }
            if (generation == searchGeneration) searchBusy.value = false
        }
    }

    fun enqueue(albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                require(albumId.matches(Regex("^[0-9]{1,12}$"))) { "作品 ID 必须是数字" }
                val taskId = UUID.randomUUID()
                val now = System.currentTimeMillis()
                dao.upsertTask(
                    DownloadTaskEntity(
                        taskId.toString(), albumId, "JM$albumId", DownloadState.QUEUED,
                        0, 0, "等待开始", now, now,
                    ),
                )
                DownloadScheduler.enqueue(getApplication(), albumId, taskId)
            }.onFailure { searchError.value = it.message ?: "无法创建任务" }
        }
    }

    fun exportPdf(album: AlbumEntity, chapters: List<ChapterEntity>, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val source = album.pdfPath?.let(::File)
                require(source?.isFile == true) { "源 PDF 已不存在" }
                val resolver = getApplication<Application>().contentResolver
                runCatching {
                    resolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                val savedName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }?.takeIf { it.isNotBlank() } ?: "${safeExportName(album.title)}.pdf"
                val now = System.currentTimeMillis()
                val taskId = UUID.randomUUID()
                dao.upsertExportTask(
                    ExportTaskEntity(
                        taskId = taskId.toString(),
                        albumId = album.albumId,
                        originalTitle = album.title,
                        savedName = savedName,
                        sourcePath = source.absolutePath,
                        destinationUri = uri.toString(),
                        destinationLabel = destinationLabel(uri, savedName),
                        author = album.author,
                        tags = album.tags,
                        description = album.description,
                        chapterCount = chapters.size,
                        pageCount = chapters.sumOf { it.pageCount },
                        sourceBytes = source.length(),
                        completedBytes = 0L,
                        state = ExportState.QUEUED,
                        message = "等待导出",
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                ExportScheduler.enqueue(getApplication(), taskId, source.absolutePath, uri.toString())
                operationMessage.value = "已加入任务"
            }.onFailure { operationMessage.value = it.message ?: "无法创建导出任务" }
        }
    }

    fun selectAlbum(albumId: String?) {
        selectedAlbumId.value = albumId
    }

    fun setArchived(albumId: String, archived: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setArchived(albumId, archived, System.currentTimeMillis())
            operationMessage.value = if (archived) "已归档" else "已移回书库"
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                dao.setPendingDeletion(albumId, true)
                WorkManager.getInstance(getApplication<Application>()).cancelAllWorkByTag("album:$albumId").result.get()
                finishAlbumDeletion(albumId)
            }.onSuccess {
                operationMessage.value = "作品及应用内文件已删除；已导出的 PDF 不受影响"
            }.onFailure { error ->
                dao.setPendingDeletion(albumId, false)
                operationMessage.value = error.message ?: "删除失败，请重试"
            }
        }
    }

    private suspend fun finishAlbumDeletion(albumId: String) {
        require(albumId.matches(Regex("^[0-9]{1,12}$"))) { "作品 ID 无效" }
        val root = File(getApplication<Application>().filesDir, "albums").canonicalFile
        val target = File(root, "jm/$albumId").canonicalFile
        require(target.path.startsWith(root.path + File.separator)) { "删除路径越界" }
        if (target.exists() && !target.deleteRecursively()) error("无法完整清理作品目录")
        dao.deleteAlbum(albumId)
    }

    fun cancel(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DownloadScheduler.cancel(getApplication(), taskId)
            val task = dao.task(taskId) ?: return@launch
            dao.updateTask(taskId, DownloadState.CANCELLED, task.completedItems, task.totalItems, "已取消，可重新下载并恢复", System.currentTimeMillis())
            val album = dao.album(task.albumId) ?: return@launch
            if (!album.pendingDeletion) {
                dao.updateAlbum(album.albumId, DownloadState.CANCELLED, album.completedChapters, album.totalChapters, album.pdfPath, System.currentTimeMillis())
            }
        }
    }

    fun deleteTaskRecord(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = dao.task(taskId) ?: return@launch
            if (task.state !in TERMINAL_STATES) {
                operationMessage.value = "运行中的任务需先取消"
                return@launch
            }
            dao.deleteTask(taskId)
            operationMessage.value = "任务记录已删除，作品仍保留"
        }
    }

    fun cancelExport(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ExportScheduler.cancel(getApplication(), taskId)
            val task = dao.exportTask(taskId) ?: return@launch
            dao.updateExportTask(taskId, ExportState.CANCELLED, task.completedBytes, "已取消", System.currentTimeMillis())
        }
    }

    fun deleteExportTaskRecord(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = dao.exportTask(taskId) ?: return@launch
            if (task.state !in EXPORT_TERMINAL_STATES) {
                operationMessage.value = "运行中的任务需先取消"
                return@launch
            }
            dao.deleteExportTask(taskId)
            operationMessage.value = "任务记录已删除，导出的 PDF 仍保留"
        }
    }

    fun updateReadPosition(albumId: String, page: Int, offset: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateReadPosition(albumId, page, offset, System.currentTimeMillis())
        }
    }

    fun consumeOperationMessage() {
        operationMessage.value = null
    }

    private fun normalizeSearchQuery(input: String): String {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("(?i)^(?:JM)?\\d{1,12}$"))) return trimmed.replace(Regex("(?i)^JM"), "")
        return Regex("(?i)/(?:album|photo)/(\\d{1,12})").find(trimmed)?.groupValues?.get(1) ?: trimmed
    }

    private fun destinationLabel(uri: Uri, savedName: String): String {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        if (!documentId.isNullOrBlank()) {
            val readable = documentId
                .replaceFirst("primary:", "内部存储/")
                .replace(':', '/')
                .replace('/', '／')
            return readable.ifBlank { savedName }
        }
        return listOfNotNull(uri.authority, savedName).joinToString(" / ")
    }

    private fun safeExportName(title: String): String = title
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
        .take(60)
        .ifBlank { "JMComic" }

    companion object {
        val TERMINAL_STATES = setOf(DownloadState.COMPLETE, DownloadState.FAILED, DownloadState.CANCELLED)
        val EXPORT_TERMINAL_STATES = setOf(ExportState.COMPLETE, ExportState.FAILED, ExportState.CANCELLED)
    }
}
