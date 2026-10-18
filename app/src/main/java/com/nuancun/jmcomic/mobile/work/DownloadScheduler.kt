package com.nuancun.jmcomic.mobile.work

import android.content.Context
import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.data.AppDatabase
import com.nuancun.jmcomic.mobile.data.ChapterEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID
import java.io.File

object DownloadScheduler {
    suspend fun enqueue(context: Context, albumId: String, taskId: UUID = UUID.randomUUID()): String {
        require(albumId.matches(Regex("^[0-9]{1,12}$"))) { "作品 ID 必须是数字" }
        val dao = AppDatabase.get(context).libraryDao()
        val needsNetwork = requiresNetwork(dao.album(albumId), dao.chapters(albumId))
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(taskId)
            .setInputData(Data.Builder().putString(DownloadWorker.KEY_ALBUM_ID, albumId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(
                if (needsNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED,
            ).build())
            .addTag("album:$albumId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("album:$albumId", ExistingWorkPolicy.REPLACE, request)
        return request.id.toString()
    }

    internal fun requiresNetwork(album: AlbumEntity?, chapters: List<ChapterEntity>): Boolean =
        album == null || !CompletedChapterFiles.hasCompleteChapters(album, chapters) ||
            album.status == DownloadState.COMPLETE && album.pdfPath?.let { File(it).isFile } == true

    fun cancel(context: Context, taskId: String) {
        runCatching { WorkManager.getInstance(context).cancelWorkById(java.util.UUID.fromString(taskId)) }
    }
}
