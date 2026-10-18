package com.nuancun.jmcomic.mobile.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nuancun.jmcomic.mobile.JMComicApplication
import com.nuancun.jmcomic.mobile.R
import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.data.AppDatabase
import com.nuancun.jmcomic.mobile.data.ChapterEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.DownloadTaskEntity
import com.nuancun.jmcomic.mobile.data.PageEntity
import com.nuancun.jmcomic.mobile.pdf.ChapterPdfInput
import com.nuancun.jmcomic.mobile.pdf.PdfBookBuilder
import com.nuancun.jmcomic.mobile.source.ImageScrambler
import com.nuancun.jmcomic.mobile.source.JmApiClient
import com.nuancun.jmcomic.mobile.source.RemotePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.currentCoroutineContext

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val dao = AppDatabase.get(context).libraryDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val albumId = inputData.getString(KEY_ALBUM_ID) ?: return@withContext Result.failure()
        DownloadRuntime.withAlbum(albumId) {
            currentCoroutineContext().ensureActive()
            download(albumId)
        }
    }

    private suspend fun download(albumId: String): Result {
        val taskId = id.toString()
        val now = System.currentTimeMillis()
        var completedImages = 0
        var totalImages = 0
        if (dao.isPendingDeletion(albumId) == true) return Result.failure()
        dao.upsertTask(DownloadTaskEntity(taskId, albumId, "JM$albumId", DownloadState.QUEUED, 0, 0, "等待开始", now, now))
        setForeground(createForeground("正在读取作品信息", 0, 0))
        try {
            val existing = dao.album(albumId)
            val savedChapters = dao.chapters(albumId)
            val albumDir = safeAlbumDir(albumId)
            val context = currentCoroutineContext()
            // A failed/cancelled final assembly must not depend on the metadata service again.
            if (existing != null && (existing.status != DownloadState.COMPLETE ||
                    existing.pdfPath?.let { File(it).isFile } != true)) {
                val localInputs = try {
                    CompletedChapterFiles.restore(existing, savedChapters, albumDir,
                        dao::pages, ::isValidImage) { context.ensureActive() }
                } catch (_: java.io.IOException) { null }
                if (localInputs != null) {
                    completedImages = localInputs.sumOf { it.images.size }
                    totalImages = completedImages
                    buildAndPublishPdf(albumId, taskId, existing.title, savedChapters, localInputs, completedImages, totalImages)
                    return Result.success()
                }
            }
            val api = JmApiClient()
            update(taskId, DownloadState.FETCHING, 0, 0, "读取作品和章节")
            val remoteAlbum = api.album(albumId)
            val existingChapters = savedChapters.associateBy { it.chapterId }
            val chapters = remoteAlbum.chapters.map { remote ->
                val saved = existingChapters[remote.id]
                ChapterEntity(
                    remote.id,
                    albumId,
                    remote.order,
                    remote.title,
                    saved?.status ?: DownloadState.QUEUED,
                    saved?.pageCount ?: 0,
                    saved?.completedPages ?: 0,
                    saved?.pdfStartPage,
                )
            }
            dao.replaceAlbumSnapshot(
                AlbumEntity(
                    albumId = albumId,
                    title = remoteAlbum.title,
                    author = remoteAlbum.authors.joinToString(", ").ifBlank { "未知作者" },
                    tags = remoteAlbum.tags.joinToString(" · "),
                    description = remoteAlbum.description,
                    status = DownloadState.DOWNLOADING,
                    totalChapters = chapters.size,
                    completedChapters = existing?.completedChapters ?: 0,
                    pdfPath = existing?.pdfPath,
                    lastReadPage = existing?.lastReadPage ?: 0,
                    lastReadOffset = existing?.lastReadOffset ?: 0,
                    archived = existing?.archived ?: false,
                    pendingDeletion = false,
                    addedAt = existing?.addedAt ?: now,
                    updatedAt = now,
                    lastReadChapterId = existing?.lastReadChapterId,
                    lastReadChapterPage = existing?.lastReadChapterPage ?: 0,
                ),
                chapters,
            )
            dao.upsertTask(DownloadTaskEntity(taskId, albumId, remoteAlbum.title, DownloadState.FETCHING, 0, 0, "准备目录", now, System.currentTimeMillis()))

            val pdfInputs = mutableListOf<ChapterPdfInput>()
            val discoveredImages = AtomicInteger()
            val progress = Mutex()
            coroutineScope {
                // Bounded look-ahead: the first chapter starts without waiting for the entire book.
                val photos = Channel<Pair<ChapterEntity, RemotePhoto>>(1)
                val metadata = launch {
                    try {
                        for ((chapterPosition, chapter) in chapters.withIndex()) {
                            ensureWritable(albumId)
                            if (chapterPosition == 0) update(taskId, DownloadState.FETCHING, 0, 0, "读取第 ${chapterPosition + 1}/${chapters.size} 章")
                            val photo = api.photo(chapter.chapterId)
                            require(photo.id == chapter.chapterId && photo.imageNames.isNotEmpty()) { "章节图片不完整" }
                            dao.updateChapterTotal(chapter.chapterId, photo.imageNames.size)
                            discoveredImages.addAndGet(photo.imageNames.size)
                            photos.send(chapter to photo)
                        }
                        photos.close()
                    } catch (error: CancellationException) {
                        photos.cancel(error)
                        throw error
                    } catch (error: Exception) {
                        // Finish the already supplied chapters before reporting a later metadata failure.
                        photos.close(error)
                    }
                }
                try {
                    for ((chapter, photo) in photos) {
                        ensureWritable(albumId)
                        totalImages = discoveredImages.get()
                        update(taskId, DownloadState.DOWNLOADING, completedImages, totalImages, "准备下载 $totalImages 张")
                        val imageDir = File(albumDir, "chapters/${photo.id}/images").apply { mkdirs() }
                        val imageFiles = photo.imageNames.indices.map { File(imageDir, "%06d.jpg".format(it + 1)) }
                        val savedPages = dao.pages(photo.id).associateBy { it.pageIndex }
                        dao.trimPages(photo.id, imageFiles.size)
                        var chapterCompleted = 0
                        val failures = downloadChapterPages(imageFiles.size) { index ->
                            ensureWritable(albumId)
                            val output = imageFiles[index]
                            val remoteName = photo.imageNames[index]
                            val saved = savedPages[index + 1]
                            if (!isValidImage(output) || saved != null &&
                                (saved.remoteName != remoteName || saved.sha256 != null && saved.sha256 != sha256(output))) {
                                downloadPage(api, photo, remoteName, output, albumId)
                            }
                            val page = PageEntity("${photo.id}:${index + 1}", photo.id, index + 1,
                                remoteName, output.absolutePath, sha256(output), output.length(), "COMPLETE")
                            progress.withLock {
                                ensureWritable(albumId)
                                dao.upsertPage(page)
                                chapterCompleted++
                                completedImages++
                                totalImages = discoveredImages.get()
                                // A previously complete chapter remains readable while being revalidated.
                                val state = if (chapter.status == DownloadState.COMPLETE &&
                                    chapter.pageCount == imageFiles.size) DownloadState.COMPLETE else DownloadState.DOWNLOADING
                                dao.updateChapter(photo.id, state, chapterCompleted, imageFiles.size, chapter.pdfStartPage)
                                update(taskId, DownloadState.DOWNLOADING, completedImages, totalImages, "已完成 $completedImages/$totalImages 张")
                                val time = System.currentTimeMillis()
                                if (time - lastNotification >= 750) {
                                    setForeground(createForeground("${remoteAlbum.title} · ${index + 1}/${photo.imageNames.size}", completedImages, totalImages))
                                    lastNotification = time
                                }
                            }
                        }
                        if (failures.isNotEmpty()) {
                            dao.updateChapter(photo.id, DownloadState.FAILED, chapterCompleted, imageFiles.size, chapter.pdfStartPage)
                            throw failures.first()
                        }
                        ensureWritable(albumId)
                        dao.updateChapter(photo.id, "COMPLETE", imageFiles.size, imageFiles.size, chapter.pdfStartPage)
                        pdfInputs += ChapterPdfInput(photo.id, chapter.title, imageFiles)
                        val completedChapters = dao.chapters(albumId).count { it.status == DownloadState.COMPLETE }
                        dao.updateAlbum(albumId, DownloadState.DOWNLOADING, completedChapters, chapters.size, existing?.pdfPath, System.currentTimeMillis())
                    }
                } finally { metadata.cancel(); photos.cancel() }
            }

            buildAndPublishPdf(albumId, taskId, remoteAlbum.title, chapters, pdfInputs, completedImages, totalImages)
            return Result.success()
        } catch (error: CancellationException) {
            withContext(NonCancellable) { cancelled(taskId, completedImages, totalImages) }
            throw error
        } catch (error: Exception) {
            if (isStopped) return cancelled(taskId, completedImages, totalImages)
            val message = error.message?.take(180) ?: error::class.java.simpleName
            update(taskId, DownloadState.FAILED, completedImages, totalImages, message)
            val savedChapters = dao.chapters(albumId)
            val completedChapters = savedChapters.count { it.status == "COMPLETE" }
            dao.updateAlbum(albumId, DownloadState.FAILED, completedChapters, savedChapters.size, dao.album(albumId)?.pdfPath, System.currentTimeMillis())
            return Result.failure()
        }
    }

    private var lastNotification = 0L

    private suspend fun buildAndPublishPdf(
        albumId: String, taskId: String, title: String, chapters: List<ChapterEntity>,
        inputs: List<ChapterPdfInput>, completedImages: Int, totalImages: Int,
    ) {
        ensureWritable(albumId)
        dao.task(taskId)?.let { dao.upsertTask(it.copy(title = title)) }
        update(taskId, DownloadState.BUILDING_PDF, completedImages, totalImages, "正在生成带章节目录的 PDF")
        setForeground(createForeground("正在生成 PDF", completedImages, totalImages))
        val pdfFile = File(safeAlbumDir(albumId), "pdf/book.pdf")
        val result = DownloadRuntime.buildPdf {
            val context = currentCoroutineContext()
            try { PdfBookBuilder.build(title, inputs, pdfFile) { context.ensureActive() } }
            catch (error: OutOfMemoryError) { throw java.io.IOException(error) }
        }
        ensureWritable(albumId)
        val counts = inputs.associate { it.chapterId to it.images.size }
        for (chapter in chapters) {
            val count = checkNotNull(counts[chapter.chapterId])
            dao.updateChapter(chapter.chapterId, "COMPLETE", count, count, result.chapterStartPages[chapter.chapterId])
        }
        dao.updateAlbum(albumId, DownloadState.COMPLETE, chapters.size, chapters.size, pdfFile.absolutePath, System.currentTimeMillis())
        update(taskId, DownloadState.COMPLETE, completedImages, totalImages, "下载完成，共 ${result.pageCount} 页")
    }

    private suspend fun ensureWritable(albumId: String) {
        currentCoroutineContext().ensureActive()
        check(dao.isPendingDeletion(albumId) != true) { "作品正在删除" }
        if (isStopped) throw CancellationException()
    }

    private suspend fun downloadPage(api: JmApiClient, photo: RemotePhoto, remoteName: String, output: File, albumId: String) {
        val raw = File(output.parentFile, "${output.name}.download")
        val part = File(output.parentFile, "${output.name}.part")
        try {
            DownloadRuntime.network.transfer({ it }, albumId) { api.downloadImage(photo.id, remoteName, raw) }
            DownloadRuntime.processImage {
                ensureWritable(albumId)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(raw.absolutePath, bounds)
                val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
                require(bounds.outWidth > 0 && bounds.outHeight > 0 && pixels <= MAX_PIXELS) { "图片尺寸无效或超过限制" }
                val source = BitmapFactory.decodeFile(raw.absolutePath,
                    BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 })
                    ?: error("图片无法解码")
                val decoded = try {
                    val segments = ImageScrambler.segmentationCount(photo.scrambleId, photo.id, remoteName.substringBeforeLast('.'))
                    ImageScrambler.decode(source, segments)
                } finally { source.recycle() }
                try {
                    FileOutputStream(part).use { stream ->
                        require(decoded.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, stream)) { "图片保存失败" }
                        stream.fd.sync()
                    }
                    ensureWritable(albumId)
                    try {
                        java.nio.file.Files.move(part.toPath(), output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    } catch (error: java.io.IOException) { throw java.io.IOException("图片提交失败", error) }
                } finally { decoded.recycle() }
            }
        } finally { raw.delete(); part.delete() }
    }

    private suspend fun update(taskId: String, state: String, completed: Int, total: Int, message: String) {
        dao.updateTask(taskId, state, completed, total, message, System.currentTimeMillis())
        setProgress(androidx.work.workDataOf("completed" to completed, "total" to total, "message" to message))
    }

    private suspend fun cancelled(taskId: String, completed: Int, total: Int): Result {
        update(taskId, DownloadState.CANCELLED, completed, total, "已取消，可重新加入任务恢复")
        val task = dao.task(taskId)
        val album = task?.let { dao.album(it.albumId) }
        if (album != null && !album.pendingDeletion) {
            dao.updateAlbum(album.albumId, DownloadState.CANCELLED, album.completedChapters, album.totalChapters, album.pdfPath, System.currentTimeMillis())
        }
        return Result.failure()
    }

    private fun safeAlbumDir(albumId: String): File {
        require(albumId.matches(Regex("^[0-9]{1,12}$")))
        val root = File(applicationContext.filesDir, "albums").canonicalFile
        val target = File(root, "jm/$albumId").canonicalFile
        require(target.path.startsWith(root.path + File.separator)) { "存储路径越界" }
        target.mkdirs()
        return target
    }

    private fun isValidImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun sha256(file: File): String {
        return CompletedChapterFiles.sha256(file)
    }

    private fun createForeground(message: String, completed: Int, total: Int): ForegroundInfo {
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification: Notification = NotificationCompat.Builder(applicationContext, JMComicApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("JMComic 本地下载")
            .setContentText(message)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(0), completed.coerceAtLeast(0), total <= 0)
            .addAction(0, "取消", cancel)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    companion object {
        const val KEY_ALBUM_ID = "album_id"
        private const val MAX_PIXELS = 32_000_000L
    }
}
