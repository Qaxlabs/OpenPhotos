package com.qaxlabs.openphotos.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Core Room entity for the unified media library table [media_items].
 * Stores essential media metadata and backup state.
 */
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["sha256"]),
        Index(value = ["backupStatus"]),
        Index(value = ["mimeType"]),
    ],
)
data class MediaEntity(
    @PrimaryKey
    val id: String,
    val filename: String,
    val mimeType: String,
    val mediaType: String, // IMAGE or VIDEO
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val createdAt: Long,
    val modifiedAt: Long,
    val sha256: String,
    val favorite: Boolean = false,
    val trashState: Boolean = false,
    val backupStatus: String, // LOCAL_ONLY, PENDING_BACKUP, UPLOADING, BACKED_UP, REMOTE_ONLY, FAILED, OVERSIZED
)
