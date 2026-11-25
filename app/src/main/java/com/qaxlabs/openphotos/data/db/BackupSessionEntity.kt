package com.qaxlabs.openphotos.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an adaptive backup session table [backup_sessions].
 */
@Entity(
    tableName = "backup_sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
    ],
)
data class BackupSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val sessionType: String, // INITIAL, CONTINUOUS, RECOVERY, USER_SELECTED
    val createdAt: Long,
    val status: String, // ACTIVE, PAUSED, COMPLETED, CANCELLED
    val totalItems: Int,
    val completedItems: Int = 0,
    val failedItems: Int = 0,
    val totalBytes: Long,
    val uploadedBytes: Long = 0L,
)
