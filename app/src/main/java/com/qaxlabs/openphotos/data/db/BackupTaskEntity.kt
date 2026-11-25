package com.qaxlabs.openphotos.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a persistent backup queue task table [backup_tasks].
 */
@Entity(
    tableName = "backup_tasks",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BackupSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["sessionId"]),
        Index(value = ["priority"]),
        Index(value = ["state"]),
        Index(value = ["nextAttemptTimestamp"]),
    ],
)
data class BackupTaskEntity(
    @PrimaryKey
    val taskId: String,
    val mediaId: String,
    val sessionId: String,
    val priority: Int, // 0 = P0 (User), 1 = P1 (New), 2 = P2 (Recent), 3 = P3 (Older), 4 = P4 (Large)
    val state: String, // QUEUED, UPLOADING, COMPLETED, FAILED, FLOOD_WAIT, ACCOUNT_RESTRICTED, CANCELLED
    val retryCount: Int = 0,
    val nextAttemptTimestamp: Long = 0L,
    val lastError: String? = null,
    val bytesUploaded: Long = 0L,
    val totalBytes: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
)
