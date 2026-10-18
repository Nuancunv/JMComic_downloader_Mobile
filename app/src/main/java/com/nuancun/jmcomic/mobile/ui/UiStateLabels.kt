package com.nuancun.jmcomic.mobile.ui

import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.ExportState

internal val TERMINAL_TASK_STATES = setOf(DownloadState.COMPLETE, DownloadState.FAILED, DownloadState.CANCELLED)
internal val TERMINAL_EXPORT_STATES = setOf(ExportState.COMPLETE, ExportState.FAILED, ExportState.CANCELLED)

internal fun statusText(status: String, completed: Int, total: Int): String = when (status) {
    DownloadState.COMPLETE -> "整部完整 · $total 章"
    DownloadState.FAILED -> "需要重试 · $completed/$total 章"
    DownloadState.CANCELLED -> "已取消 · $completed/$total 章"
    else -> "下载中 · $completed/$total 章"
}

internal fun stateLabel(state: String): String = when (state) {
    DownloadState.QUEUED -> "等待开始"
    DownloadState.FETCHING -> "读取目录"
    DownloadState.DOWNLOADING -> "下载图片"
    DownloadState.BUILDING_PDF -> "生成 PDF"
    DownloadState.COMPLETE -> "已完成"
    DownloadState.FAILED -> "失败"
    DownloadState.CANCELLED -> "已取消"
    else -> state
}

internal fun exportStateLabel(state: String): String = when (state) {
    ExportState.QUEUED -> "等待导出"
    ExportState.EXPORTING -> "导出 PDF"
    ExportState.COMPLETE -> "已完成"
    ExportState.FAILED -> "失败"
    ExportState.CANCELLED -> "已取消"
    else -> state
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

internal fun formatTaskTime(time: Long): String = java.text.DateFormat
    .getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
    .format(java.util.Date(time))

