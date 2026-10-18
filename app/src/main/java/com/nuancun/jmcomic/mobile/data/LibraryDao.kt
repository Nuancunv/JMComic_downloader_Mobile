package com.nuancun.jmcomic.mobile.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM albums WHERE pendingDeletion = 0 ORDER BY updatedAt DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE albumId = :albumId AND pendingDeletion = 0")
    fun observeAlbum(albumId: String): Flow<AlbumEntity?>

    @Transaction
    @Query("SELECT * FROM albums WHERE archived = :archived AND pendingDeletion = 0 ORDER BY updatedAt DESC")
    fun observeSearchableAlbums(archived: Boolean): Flow<List<SearchableAlbum>>

    @Query("SELECT COUNT(*) FROM albums WHERE archived = 1 AND pendingDeletion = 0")
    fun observeArchivedCount(): Flow<Int>

    @Query("SELECT * FROM chapters WHERE albumId = :albumId ORDER BY chapterOrder")
    fun observeChapters(albumId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM download_tasks ORDER BY updatedAt DESC")
    fun observeTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM export_tasks ORDER BY updatedAt DESC")
    fun observeExportTasks(): Flow<List<ExportTaskEntity>>

    @Query("SELECT * FROM albums WHERE albumId = :albumId")
    suspend fun album(albumId: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE pendingDeletion = 1")
    suspend fun pendingDeletionAlbums(): List<AlbumEntity>

    @Query("SELECT pendingDeletion FROM albums WHERE albumId = :albumId")
    suspend fun isPendingDeletion(albumId: String): Boolean?

    @Query("SELECT * FROM chapters WHERE albumId = :albumId ORDER BY chapterOrder")
    suspend fun chapters(albumId: String): List<ChapterEntity>

    @Query("SELECT * FROM pages WHERE chapterId = :chapterId ORDER BY pageIndex")
    suspend fun pages(chapterId: String): List<PageEntity>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId")
    suspend fun task(taskId: String): DownloadTaskEntity?

    @Query("SELECT * FROM export_tasks WHERE taskId = :taskId")
    suspend fun exportTask(taskId: String): ExportTaskEntity?

    @Upsert
    suspend fun upsertAlbum(album: AlbumEntity)

    @Upsert
    suspend fun upsertChapters(chapters: List<ChapterEntity>)

    @Upsert
    suspend fun upsertPage(page: PageEntity)

    @Upsert
    suspend fun upsertTask(task: DownloadTaskEntity)

    @Upsert
    suspend fun upsertExportTask(task: ExportTaskEntity)

    @Query("UPDATE download_tasks SET state=:state, completedItems=:completed, totalItems=:total, message=:message, updatedAt=:now WHERE taskId=:taskId")
    suspend fun updateTask(taskId: String, state: String, completed: Int, total: Int, message: String, now: Long)

    @Query("UPDATE export_tasks SET state=:state, completedBytes=:completed, message=:message, updatedAt=:now WHERE taskId=:taskId")
    suspend fun updateExportTask(taskId: String, state: String, completed: Long, message: String, now: Long)

    @Query("UPDATE chapters SET status=:status, completedPages=:completed, pageCount=:total, pdfStartPage=:startPage WHERE chapterId=:chapterId")
    suspend fun updateChapter(chapterId: String, status: String, completed: Int, total: Int, startPage: Int?)

    @Query("UPDATE chapters SET status=CASE WHEN pageCount != :total THEN 'DOWNLOADING' ELSE status END, pageCount=:total WHERE chapterId=:chapterId")
    suspend fun updateChapterTotal(chapterId: String, total: Int)

    @Query("DELETE FROM pages WHERE chapterId=:chapterId AND pageIndex > :total")
    suspend fun trimPages(chapterId: String, total: Int)

    @Query("UPDATE albums SET status=:status, completedChapters=:completed, totalChapters=:total, pdfPath=:pdfPath, updatedAt=:now WHERE albumId=:albumId")
    suspend fun updateAlbum(albumId: String, status: String, completed: Int, total: Int, pdfPath: String?, now: Long)

    @Query("UPDATE albums SET lastReadPage=:page, updatedAt=:now WHERE albumId=:albumId")
    suspend fun updateReadPage(albumId: String, page: Int, now: Long)

    @Query("UPDATE albums SET lastReadPage=:page, lastReadOffset=:offset, updatedAt=:now WHERE albumId=:albumId")
    suspend fun updateReadPosition(albumId: String, page: Int, offset: Int, now: Long)

    @Query("UPDATE albums SET lastReadPage=:page, lastReadOffset=:offset, lastReadChapterId=:chapterId, lastReadChapterPage=:chapterPage, updatedAt=:now WHERE albumId=:albumId")
    suspend fun updateReadPosition(albumId: String, page: Int, offset: Int, chapterId: String?, chapterPage: Int, now: Long)

    @Query("UPDATE albums SET archived=:archived, updatedAt=:now WHERE albumId=:albumId")
    suspend fun setArchived(albumId: String, archived: Boolean, now: Long)

    @Query("UPDATE albums SET pendingDeletion=:pending WHERE albumId=:albumId")
    suspend fun setPendingDeletion(albumId: String, pending: Boolean)

    @Query("DELETE FROM albums WHERE albumId=:albumId")
    suspend fun deleteAlbum(albumId: String)

    @Query("DELETE FROM download_tasks WHERE taskId=:taskId")
    suspend fun deleteTask(taskId: String)

    @Query("DELETE FROM export_tasks WHERE taskId=:taskId")
    suspend fun deleteExportTask(taskId: String)

    @Transaction
    suspend fun replaceAlbumSnapshot(album: AlbumEntity, chapters: List<ChapterEntity>) {
        check(isPendingDeletion(album.albumId) != true) { "作品正在删除" }
        val current = this.album(album.albumId)
        upsertAlbum(if (current == null) album else album.copy(
            lastReadPage = current.lastReadPage,
            lastReadOffset = current.lastReadOffset,
            lastReadChapterId = current.lastReadChapterId,
            lastReadChapterPage = current.lastReadChapterPage,
            archived = current.archived,
        ))
        upsertChapters(chapters)
    }
}
