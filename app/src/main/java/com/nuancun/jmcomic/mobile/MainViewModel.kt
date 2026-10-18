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
import com.nuancun.jmcomic.mobile.data.LibrarySearchHit
import com.nuancun.jmcomic.mobile.data.LocalSearchRepository
import com.nuancun.jmcomic.mobile.data.SearchableAlbum
import com.nuancun.jmcomic.mobile.source.JmApiClient
import com.nuancun.jmcomic.mobile.source.SearchHit
import com.nuancun.jmcomic.mobile.source.MatchKind
import com.nuancun.jmcomic.mobile.source.QueryAnalyzer
import com.nuancun.jmcomic.mobile.source.RemoteSearchSession
import com.nuancun.jmcomic.mobile.source.SearchQuery
import com.nuancun.jmcomic.mobile.work.DownloadScheduler
import com.nuancun.jmcomic.mobile.work.ExportScheduler
import com.nuancun.jmcomic.mobile.work.DownloadRuntime
import com.nuancun.jmcomic.mobile.reader.ReadingIndex
import com.nuancun.jmcomic.mobile.reader.ReadingPosition
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).libraryDao()
    private val api = JmApiClient()
    private var searchGeneration = 0
    private var activeSearchSession: RemoteSearchSession? = null
    private var searchJob: Job? = null
    private val localSearch = LocalSearchRepository(dao::observeSearchableAlbums)

    val albums: StateFlow<List<AlbumEntity>> = dao.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val localQuery = MutableStateFlow("")
    val archiveQuery = MutableStateFlow("")
    val libraryAlbums: StateFlow<List<LibrarySearchHit>> = localSearch.observe(localQuery, false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val archivedAlbums: StateFlow<List<LibrarySearchHit>> = localSearch.observe(archiveQuery, true)
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
    val searchQuery = MutableStateFlow<SearchQuery?>(null)
    val searchSuggestions: StateFlow<List<String>> = combine(searchInput.debounce(180), albums) { input, entries ->
        val analyzed = QueryAnalyzer.analyze(input)
        if (analyzed.isEmpty || analyzed.id != null) emptyList()
        else LocalSearchRepository.search(input, entries.map { SearchableAlbum(it, emptyList()) })
            .filter { it.match.kind >= MatchKind.TITLE_SIMILAR }.map { it.album.title }.distinct().take(5)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val searchBusy = MutableStateFlow(false)
    val searchCanLoadMore = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)
    val operationMessage = MutableStateFlow<String?>(null)

    fun observeAlbum(albumId: String) = dao.observeAlbum(albumId).distinctUntilChanged()
    fun observeAlbumChapters(albumId: String) = dao.observeChapters(albumId).distinctUntilChanged()
    fun readingAlbum(albumId: String): AutoCloseable = DownloadRuntime.reading(albumId)
    fun observeReadingIndex(albumId: String) = dao.observeChapters(albumId)
        .map { chapters -> chapters.map { it.copy(completedPages = 0) } }
        .distinctUntilChanged()
        .map { chapters ->
            val pages = chapters.filter { it.status == DownloadState.COMPLETE }.flatMap { dao.pages(it.chapterId) }
                .filter { page -> page.localPath?.let { File(it).let { file -> file.isFile && file.length() > 0 } } == true }
            ReadingIndex(chapters, pages)
        }.distinctUntilChanged { old, new -> old.entries == new.entries }.flowOn(Dispatchers.IO)

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
        val generation = ++searchGeneration
        searchJob?.cancel()
        activeSearchSession = null
        searchQuery.value = null
        searchResults.value = emptyList()
        searchCanLoadMore.value = false
        searchError.value = null
        if (query.isBlank()) {
            searchBusy.value = false
            return
        }
        executeSearch(generation, input = query)
    }

    fun loadMoreSearchResults() {
        if (searchBusy.value || !searchCanLoadMore.value) return
        val session = activeSearchSession ?: return
        executeSearch(searchGeneration, session)
    }

    private fun executeSearch(generation: Int, existing: RemoteSearchSession? = null, input: String = "") {
        searchBusy.value = true
        searchError.value = null
        searchJob = viewModelScope.launch {
            try {
                val session = existing ?: withContext(Dispatchers.Default) { RemoteSearchSession(input, api) }
                if (generation != searchGeneration) return@launch
                activeSearchSession = session
                searchQuery.value = session.query
                val batch = withContext(Dispatchers.Default) { session.loadNext() }
                if (generation != searchGeneration) return@launch
                searchResults.value = batch.hits
                searchCanLoadMore.value = batch.hasMore
                searchError.value = batch.notice
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == searchGeneration) searchError.value = "检索失败，请重试"
            } finally {
                if (generation == searchGeneration) searchBusy.value = false
            }
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
        DownloadRuntime.withAlbum(albumId) { deleteAlbumFiles(albumId) }
    }

    private suspend fun deleteAlbumFiles(albumId: String) {
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

    fun updateReadPosition(albumId: String, position: ReadingPosition, offset: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateReadPosition(albumId, position.page, offset, position.chapterId, position.chapterPage, System.currentTimeMillis())
        }
    }

    fun consumeOperationMessage() {
        operationMessage.value = null
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
