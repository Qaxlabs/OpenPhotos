package com.qaxlabs.openphotos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Application Room database — the unified local and vault cache.
 *
 * Version history:
 *   v1 — [UploadedItemEntity]: records files uploaded to Telegram.
 *   v2 — Index cache updates.
 *   v3 — Added thumbnailRemoteId column.
 *   v4 — Unified local and cloud media library tables ([MediaEntity], [LocalMediaMetadataEntity],
 *        [RemoteMediaMetadataEntity], [BackupTaskEntity], [AlbumEntity], [ItemAlbumCrossRef]).
 */
@Database(
    entities = [
        UploadedItemEntity::class,
        MediaEntity::class,
        LocalMediaMetadataEntity::class,
        RemoteMediaMetadataEntity::class,
        BackupTaskEntity::class,
        BackupSessionEntity::class,
        AlbumEntity::class,
        ItemAlbumCrossRef::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun uploadedItemDao(): UploadedItemDao
    abstract fun unifiedMediaDao(): UnifiedMediaDao
    abstract fun backupTaskDao(): BackupTaskDao
    abstract fun albumDao(): AlbumDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `media_items` (
                        `id` TEXT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `width` INTEGER NOT NULL DEFAULT 0,
                        `height` INTEGER NOT NULL DEFAULT 0,
                        `durationMs` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `modifiedAt` INTEGER NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `favorite` INTEGER NOT NULL DEFAULT 0,
                        `trashState` INTEGER NOT NULL DEFAULT 0,
                        `backupStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_createdAt` ON `media_items` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_sha256` ON `media_items` (`sha256`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_backupStatus` ON `media_items` (`backupStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_mimeType` ON `media_items` (`mimeType`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `local_media_metadata` (
                        `mediaId` TEXT NOT NULL,
                        `localUri` TEXT NOT NULL,
                        `devicePath` TEXT,
                        `dateAdded` INTEGER NOT NULL,
                        PRIMARY KEY(`mediaId`),
                        FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `remote_media_metadata` (
                        `mediaId` TEXT NOT NULL,
                        `telegramChatId` INTEGER NOT NULL,
                        `telegramMessageId` INTEGER NOT NULL,
                        `thumbnailRemoteId` TEXT,
                        `uploadedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`mediaId`),
                        FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `backup_tasks` (
                        `mediaId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `bytesUploaded` INTEGER NOT NULL DEFAULT 0,
                        `totalBytes` INTEGER NOT NULL DEFAULT 0,
                        `lastError` TEXT,
                        `retriesCount` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`mediaId`),
                        FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `albums` (
                        `albumId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `coverMediaId` TEXT,
                        PRIMARY KEY(`albumId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_albums_title` ON `albums` (`title`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `album_media_cross_ref` (
                        `albumId` TEXT NOT NULL,
                        `mediaId` TEXT NOT NULL,
                        PRIMARY KEY(`albumId`, `mediaId`),
                        FOREIGN KEY(`albumId`) REFERENCES `albums`(`albumId`) ON DELETE CASCADE,
                        FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_media_cross_ref_mediaId` ON `album_media_cross_ref` (`mediaId`)")

                // Migrate legacy records from uploaded_items table if it exists
                db.execSQL("""
                    INSERT OR IGNORE INTO `media_items` (`id`, `filename`, `mimeType`, `mediaType`, `sizeBytes`, `width`, `height`, `durationMs`, `createdAt`, `modifiedAt`, `sha256`, `favorite`, `trashState`, `backupStatus`)
                    SELECT 
                        `indexId` AS `id`,
                        `displayName` AS `filename`,
                        `mimeType`,
                        CASE WHEN `mimeType` LIKE 'video/%' THEN 'VIDEO' ELSE 'IMAGE' END AS `mediaType`,
                        `sizeBytes`,
                        0 AS `width`,
                        0 AS `height`,
                        0 AS `durationMs`,
                        `takenAt` AS `createdAt`,
                        `uploadedAt` AS `modifiedAt`,
                        `sha256`,
                        0 AS `favorite`,
                        0 AS `trashState`,
                        'BACKED_UP' AS `backupStatus`
                    FROM `uploaded_items`
                """.trimIndent())

                db.execSQL("""
                    INSERT OR IGNORE INTO `remote_media_metadata` (`mediaId`, `telegramChatId`, `telegramMessageId`, `thumbnailRemoteId`, `uploadedAt`)
                    SELECT 
                        `indexId` AS `mediaId`,
                        `telegramChatId`,
                        `telegramMessageId`,
                        `thumbnailRemoteId`,
                        `uploadedAt`
                    FROM `uploaded_items`
                """.trimIndent())

                db.execSQL("""
                    INSERT OR IGNORE INTO `local_media_metadata` (`mediaId`, `localUri`, `devicePath`, `dateAdded`)
                    SELECT 
                        `indexId` AS `mediaId`,
                        `localUri`,
                        NULL AS `devicePath`,
                        `takenAt` AS `dateAdded`
                    FROM `uploaded_items`
                    WHERE `localUri` IS NOT NULL AND `localUri` != ''
                """.trimIndent())
            }
        }
    }
}
