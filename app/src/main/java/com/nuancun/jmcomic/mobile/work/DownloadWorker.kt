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
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val dao = AppDatabase.get(context).libraryDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val albumId = inputData.getString(KEY_ALBUM_ID) ?: return@withContext Result.failure()
        val taskId = id.toString()
        val now = System.currentTimeMillis()
        var completedImages = 0
        var totalImages = 0
        if (dao.isPendingDeletion(albumId) == true) return@withContext Result.failure()
        dao.upsertTask(DownloadTaskEntity(taskId, albumId, "JM$albumId", DownloadState.QUEUED, 0, 0, "等待开始", now, now))
        setForeground(createForeground("正在读取作品信息", 0, 0))
        try {
            val api = JmApiClient()
            update(taskId, DownloadState.FETCHING, 0, 0, "读取作品和章节")
            val remoteAlbum = api.album(albumId)
            val existing = dao.album(albumId)
            val existingChapters = dao.chapters(albumId).associateBy { it.chapterId }
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
                ),
                chapters,
            )
            dao.upsertTask(DownloadTaskEntity(taskId, albumId, remoteAlbum.title, DownloadState.FETCHING, 0, 0, "准备目录", now, System.currentTimeMillis()))

            val chapterPhotos = mutableListOf<Pair<ChapterEntity, RemotePhoto>>()
            for ((chapterPosition, chapter) in chapters.withIndex()) {
                coroutineContext.ensureActive()
                check(dao.isPendingDeletion(albumId) != true) { "作品正在删除" }
                if (isStopped) return@withContext cancelled(taskId, completedImages, totalImages)
                update(taskId, DownloadState.FETCHING, 0, 0, "读取第 ${chapterPosition + 1}/${chapters.size} 章")
                val photo = api.photo(chapter.chapterId)
                chapterPhotos += chapter to photo
                totalImages += photo.imageNames.size
            }
            update(taskId, DownloadState.DOWNLOADING, 0, totalImages, "准备下载 $totalImages 张")

            val albumDir = safeAlbumDir(albumId)
            val pdfInputs = mutableListOf<ChapterPdfInput>()
            for ((chapterPosition, pair) in chapterPhotos.withIndex()) {
                val (chapter, photo) = pair
                coroutineContext.ensureActive()
                check(dao.isPendingDeletion(albumId) != true) { "作品正在删除" }
                if (isStopped) return@withContext cancelled(taskId, completedImages, totalImages)
                val imageDir = File(albumDir, "chapters/${photo.id}/images").apply { mkdirs() }
                val imageFiles = mutableListOf<File>()
                photo.imageNames.forEachIndexed { index, remoteName ->
                    coroutineContext.ensureActive()
                    check(dao.isPendingDeletion(albumId) != true) { "作品正在删除" }
                    if (isStopped) return@withContext cancelled(taskId, completedImages, totalImages)
                    val output = File(imageDir, "%06d.jpg".format(index + 1))
                    val pageKey = "${photo.id}:${index + 1}"
                    if (!isValidImage(output)) {
                        update(taskId, DownloadState.DOWNLOADING, completedImages, totalImages, "${chapter.title} · ${index + 1}/${photo.imageNames.size}")
                        setForeground(createForeground("${remoteAlbum.title} · ${index + 1}/${photo.imageNames.size}", completedImages, totalImages))
                        val bytes = api.downloadImage(photo.id, remoteName)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
                        require(bounds.outWidth > 0 && bounds.outHeight > 0 && pixels <= MAX_PIXELS) { "图片尺寸无效或超过限制" }
                        val source = BitmapFactory.decodeByteArray(
                            bytes,
                            0,
                            bytes.size,
                            BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 },
                        ) ?: error("图片无法解码")
                        val decoded = try {
                            val segments = ImageScrambler.segmentationCount(photo.scrambleId, photo.id, remoteName.substringBeforeLast('.'))
                            ImageScrambler.decode(source, segments)
                        } finally {
                            source.recycle()
                        }
                        val part = File(imageDir, "${output.name}.part")
                        try {
                            FileOutputStream(part).use { stream ->
                                require(decoded.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, stream)) { "图片保存失败" }
                                stream.fd.sync()
                            }
                            if (output.exists()) output.delete()
                            require(part.renameTo(output)) { "图片提交失败" }
                        } finally {
                            decoded.recycle()
                            if (part.exists()) part.delete()
                        }
                    }
                    completedImages++
                    dao.upsertPage(PageEntity(pageKey, photo.id, index + 1, remoteName, output.absolutePath, sha256(output), output.length(), "COMPLETE"))
                    dao.updateChapter(photo.id, "DOWNLOADING", index + 1, photo.imageNames.size, null)
                    imageFiles += output
                    update(taskId, DownloadState.DOWNLOADING, completedImages, totalImages, "已完成 $completedImages/$totalImages 张")
                }
                dao.updateChapter(photo.id, "COMPLETE", imageFiles.size, imageFiles.size, null)
                pdfInputs += ChapterPdfInput(photo.id, chapter.title, imageFiles)
                dao.updateAlbum(albumId, DownloadState.DOWNLOADING, chapterPosition + 1, chapters.size, existing?.pdfPath, System.currentTimeMillis())
            }

            update(taskId, DownloadState.BUILDING_PDF, completedImages, totalImages, "正在生成带章节目录的 PDF")
            setForeground(createForeground("正在生成 PDF", completedImages, totalImages))
            val pdfFile = File(albumDir, "pdf/book.pdf")
            val result = PdfBookBuilder.build(remoteAlbum.title, pdfInputs, pdfFile)
            chapters.forEach { chapter ->
                val pages = dao.pages(chapter.chapterId)
                dao.updateChapter(chapter.chapterId, "COMPLETE", pages.size, pages.size, result.chapterStartPages[chapter.chapterId])
            }
            dao.updateAlbum(albumId, DownloadState.COMPLETE, chapters.size, chapters.size, pdfFile.absolutePath, System.currentTimeMillis())
            update(taskId, DownloadState.COMPLETE, completedImages, totalImages, "下载完成，共 ${result.pageCount} 页")
            Result.success()
        } catch (error: CancellationException) {
            withContext(NonCancellable) { cancelled(taskId, completedImages, totalImages) }
        } catch (error: Throwable) {
            if (isStopped) return@withContext cancelled(taskId, completedImages, totalImages)
            val message = error.message?.take(180) ?: error::class.java.simpleName
            update(taskId, DownloadState.FAILED, completedImages, totalImages, message)
            val savedChapters = dao.chapters(albumId)
            val completedChapters = savedChapters.count { it.status == "COMPLETE" }
            dao.updateAlbum(albumId, DownloadState.FAILED, completedChapters, savedChapters.size, dao.album(albumId)?.pdfPath, System.currentTimeMillis())
            Result.failure()
        }
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
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
