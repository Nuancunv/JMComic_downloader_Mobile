package com.nuancun.jmcomic.mobile.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID

object ExportScheduler {
    fun enqueue(context: Context, taskId: UUID, sourcePath: String, destinationUri: String) {
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setId(taskId)
            .setInputData(
                Data.Builder()
                    .putString(ExportWorker.KEY_SOURCE_PATH, sourcePath)
                    .putString(ExportWorker.KEY_DESTINATION_URI, destinationUri)
                    .build(),
            )
            .addTag("export:$taskId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("export:$taskId", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, taskId: String) {
        runCatching { WorkManager.getInstance(context).cancelWorkById(UUID.fromString(taskId)) }
    }
}
