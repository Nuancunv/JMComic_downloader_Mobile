package com.nuancun.jmcomic.mobile.work

import com.nuancun.jmcomic.mobile.data.AlbumEntity
import com.nuancun.jmcomic.mobile.data.ChapterEntity
import com.nuancun.jmcomic.mobile.data.DownloadState
import com.nuancun.jmcomic.mobile.data.PageEntity
import com.nuancun.jmcomic.mobile.pdf.ChapterPdfInput
import java.io.File
import java.security.MessageDigest

/** A PDF retry may use a complete, verified local snapshot without consulting the API. */
internal object CompletedChapterFiles {
    fun hasCompleteChapters(album: AlbumEntity, chapters: List<ChapterEntity>): Boolean =
        !album.pendingDeletion && chapters.isNotEmpty() && chapters.size == album.totalChapters &&
            chapters.map { it.chapterId }.distinct().size == chapters.size && chapters.all { chapter ->
                chapter.albumId == album.albumId && chapter.chapterId.matches(Regex("^[0-9]{1,12}$")) &&
                    chapter.status == DownloadState.COMPLETE && chapter.pageCount > 0 &&
                    chapter.completedPages == chapter.pageCount
            }

    suspend fun restore(
        album: AlbumEntity,
        chapters: List<ChapterEntity>,
        directory: File,
        pages: suspend (String) -> List<PageEntity>,
        validImage: (File) -> Boolean,
        checkActive: () -> Unit,
    ): List<ChapterPdfInput>? {
        if (!hasCompleteChapters(album, chapters)) return null
        val root = directory.canonicalFile
        val inputs = mutableListOf<ChapterPdfInput>()
        for (chapter in chapters.sortedBy { it.chapterOrder }) {
            checkActive()
            val rows = pages(chapter.chapterId).sortedBy { it.pageIndex }
            if (rows.size != chapter.pageCount) return null
            val files = mutableListOf<File>()
            for ((index, page) in rows.withIndex()) {
                checkActive()
                val file = File(root, "chapters/${chapter.chapterId}/images/%06d.jpg".format(index + 1)).canonicalFile
                if (!file.path.startsWith(root.path + File.separator) || page.chapterId != chapter.chapterId ||
                    page.pageIndex != index + 1 || page.status != DownloadState.COMPLETE ||
                    page.localPath == null || File(page.localPath).canonicalFile != file ||
                    !file.isFile || file.length() != page.byteCount || page.sha256.isNullOrBlank() ||
                    !validImage(file) || sha256(file, checkActive) != page.sha256) return null
                files += file
            }
            inputs += ChapterPdfInput(chapter.chapterId, chapter.title, files)
        }
        return inputs
    }

    fun sha256(file: File, checkActive: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
