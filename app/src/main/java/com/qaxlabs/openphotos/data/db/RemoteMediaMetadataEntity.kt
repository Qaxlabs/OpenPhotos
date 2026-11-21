package com.qaxlabs.openphotos.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Room entity storing Telegram-specific properties for cloud-backed media files.
 */
@Entity(
    tableName = "remote_media_metadata",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RemoteMediaMetadataEntity(
    @PrimaryKey
    val mediaId: String,
    val telegramChatId: Long,
    val telegramMessageId: Long,
    val thumbnailRemoteId: String?,
    val uploadedAt: Long,
)
