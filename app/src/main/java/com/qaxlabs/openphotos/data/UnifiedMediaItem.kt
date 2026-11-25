package com.qaxlabs.openphotos.data

import android.net.Uri

/**
 * Represents the type of media file.
 */
enum class MediaType {
    IMAGE,
    VIDEO,
}

/**
 * Represents the backup status of a media item in the unified library.
 */
enum class BackupStatus {
    /** Media exists only on the local device MediaStore. */
    LOCAL_ONLY,

    /** Media is queued or scheduled for backup to Telegram. */
    PENDING_BACKUP,

    /** Media is currently uploading to Telegram. */
    UPLOADING,

    /** Media exists both locally and backed up in Telegram. */
    BACKED_UP,

    /** Media exists only in Telegram cloud (not stored locally). */
    REMOTE_ONLY,

    /** Media backup attempt failed. */
    FAILED,

    /** Media file exceeds free account size limit (2 GB). */
    OVERSIZED,
}

/**
 * Domain model for a unified media item representing both local device media
 * and Telegram cloud media in a single seamless entity.
 */
data class UnifiedMediaItem(
    val id: String,
    val filename: String,
    val mimeType: String,
    val mediaType: MediaType,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val createdAt: Long,
    val modifiedAt: Long = System.currentTimeMillis(),
    val sha256: String,
    val favorite: Boolean = false,
    val trashState: Boolean = false,
    val backupStatus: BackupStatus,
    val localUri: Uri? = null,
    val devicePath: String? = null,
    val telegramChatId: Long? = null,
    val telegramMessageId: Long? = null,
    val thumbnailRemoteId: String? = null,
    val uploadedAt: Long? = null,
)
