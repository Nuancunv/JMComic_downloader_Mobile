package com.nuancun.jmcomic.mobile.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlbumEntity::class, ChapterEntity::class, PageEntity::class, DownloadTaskEntity::class, ExportTaskEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "library.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE albums ADD COLUMN lastReadOffset INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE albums ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE albums ADD COLUMN pendingDeletion INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE albums ADD COLUMN lastReadChapterId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE albums ADD COLUMN lastReadChapterPage INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `export_tasks` (
                        `taskId` TEXT NOT NULL,
                        `albumId` TEXT NOT NULL,
                        `originalTitle` TEXT NOT NULL,
                        `savedName` TEXT NOT NULL,
                        `sourcePath` TEXT NOT NULL,
                        `destinationUri` TEXT NOT NULL,
                        `destinationLabel` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `chapterCount` INTEGER NOT NULL,
                        `pageCount` INTEGER NOT NULL,
                        `sourceBytes` INTEGER NOT NULL,
                        `completedBytes` INTEGER NOT NULL,
                        `state` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`taskId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_export_tasks_albumId` ON `export_tasks` (`albumId`)")
            }
        }
    }
}
