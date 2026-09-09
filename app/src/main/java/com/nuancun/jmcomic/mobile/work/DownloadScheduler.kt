package com.nuancun.jmcomic.mobile.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID

object DownloadScheduler {
    fun enqueue(context: Context, albumId: String, taskId: UUID = UUID.randomUUID()): String {
        require(albumId.matches(Regex("^[0-9]{1,12}$"))) { "作品 ID 必须是数字" }
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(taskId)
            .setInputData(Data.Builder().putString(DownloadWorker.KEY_ALBUM_ID, albumId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("album:$albumId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("album:$albumId", ExistingWorkPolicy.REPLACE, request)
        return request.id.toString()
    }

    fun cancel(context: Context, taskId: String) {
        runCatching { WorkManager.getInstance(context).cancelWorkById(java.util.UUID.fromString(taskId)) }
    }
}
