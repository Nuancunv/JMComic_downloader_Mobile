package com.nuancun.jmcomic.mobile.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nuancun.jmcomic.mobile.JMComicApplication
import com.nuancun.jmcomic.mobile.R
import com.nuancun.jmcomic.mobile.data.AppDatabase
import com.nuancun.jmcomic.mobile.data.ExportState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class ExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val dao = AppDatabase.get(context).libraryDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = id.toString()
        val sourceValue = inputData.getString(KEY_SOURCE_PATH) ?: return@withContext Result.failure()
        val destinationValue = inputData.getString(KEY_DESTINATION_URI) ?: return@withContext Result.failure()
        val task = dao.exportTask(taskId) ?: return@withContext Result.failure()
        var completed = task.completedBytes
        try {
            val root = File(applicationContext.filesDir, "albums").canonicalFile
            val source = File(sourceValue).canonicalFile
            require(source.path.startsWith(root.path + File.separator) && source.isFile) { "源 PDF 已不存在" }
            val destination = Uri.parse(destinationValue)
            require(destination.scheme == "content") { "导出位置无效" }
            val total = source.length().coerceAtLeast(1L)
            val publishStep = maxOf(256L * 1024L, total / 200L)
            var lastPublished = 0L
            update(taskId, ExportState.EXPORTING, 0L, "正在导出")
            setForeground(createForeground(task.savedName, 0L, total))
            applicationContext.contentResolver.openOutputStream(destination, "w")?.use { output ->
                source.inputStream().buffered().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        if (isStopped) throw CancellationException("导出已取消")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        completed += count
                        if (completed - lastPublished >= publishStep || completed >= total) {
                            update(taskId, ExportState.EXPORTING, completed, "正在导出")
                            setForeground(createForeground(task.savedName, completed, total))
                            lastPublished = completed
                        }
                    }
                    output.flush()
                }
            } ?: error("无法打开导出位置")
            update(taskId, ExportState.COMPLETE, total, "导出完成")
            Result.success()
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                update(taskId, ExportState.CANCELLED, completed, "已取消")
            }
            Result.failure()
        } catch (error: Throwable) {
            val message = error.message?.take(180) ?: "导出失败"
            update(taskId, ExportState.FAILED, completed, message)
            Result.failure()
        }
    }

    private suspend fun update(taskId: String, state: String, completed: Long, message: String) {
        dao.updateExportTask(taskId, state, completed, message, System.currentTimeMillis())
        setProgress(androidx.work.workDataOf("completed" to completed, "message" to message))
    }

    private fun createForeground(name: String, completed: Long, total: Long): ForegroundInfo {
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val percent = ((completed.coerceIn(0L, total) * 100L) / total.coerceAtLeast(1L)).toInt()
        val notification: Notification = NotificationCompat.Builder(applicationContext, JMComicApplication.EXPORT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("正在导出 PDF")
            .setContentText(name)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, false)
            .addAction(0, "取消", cancel)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    companion object {
        const val KEY_SOURCE_PATH = "source_path"
        const val KEY_DESTINATION_URI = "destination_uri"
    }
}
