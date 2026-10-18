package com.nuancun.jmcomic.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val albumId: String,
    val title: String,
    val author: String,
    val tags: String,
    val description: String,
    val status: String,
    val totalChapters: Int,
    val completedChapters: Int,
    val pdfPath: String?,
    val lastReadPage: Int,
    @ColumnInfo(defaultValue = "0") val lastReadOffset: Int = 0,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,
    val addedAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val lastReadChapterId: String? = null,
    @ColumnInfo(defaultValue = "0") val lastReadChapterPage: Int = 0,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = AlbumEntity::class,
        parentColumns = ["albumId"],
        childColumns = ["albumId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("albumId")],
)
data class ChapterEntity(
    @PrimaryKey val chapterId: String,
    val albumId: String,
    val chapterOrder: Int,
    val title: String,
    val status: String,
    val pageCount: Int,
    val completedPages: Int,
    val pdfStartPage: Int?,
)

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = ChapterEntity::class,
        parentColumns = ["chapterId"],
        childColumns = ["chapterId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("chapterId")],
)
data class PageEntity(
    @PrimaryKey val pageKey: String,
    val chapterId: String,
    val pageIndex: Int,
    val remoteName: String,
    val localPath: String?,
    val sha256: String?,
    val byteCount: Long,
    val status: String,
)

@Entity(tableName = "download_tasks", indices = [Index("albumId")])
data class DownloadTaskEntity(
    @PrimaryKey val taskId: String,
    val albumId: String,
    val title: String,
    val state: String,
    val completedItems: Int,
    val totalItems: Int,
    val message: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "export_tasks", indices = [Index("albumId")])
data class ExportTaskEntity(
    @PrimaryKey val taskId: String,
    val albumId: String,
    val originalTitle: String,
    val savedName: String,
    val sourcePath: String,
    val destinationUri: String,
    val destinationLabel: String,
    val author: String,
    val tags: String,
    val description: String,
    val chapterCount: Int,
    val pageCount: Int,
    val sourceBytes: Long,
    val completedBytes: Long,
    val state: String,
    val message: String,
    val createdAt: Long,
    val updatedAt: Long,
)

object DownloadState {
    const val QUEUED = "QUEUED"
    const val FETCHING = "FETCHING"
    const val DOWNLOADING = "DOWNLOADING"
    const val BUILDING_PDF = "BUILDING_PDF"
    const val COMPLETE = "COMPLETE"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

object ExportState {
    const val QUEUED = "QUEUED"
    const val EXPORTING = "EXPORTING"
    const val COMPLETE = "COMPLETE"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}
