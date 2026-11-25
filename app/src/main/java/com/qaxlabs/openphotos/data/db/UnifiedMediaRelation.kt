package com.qaxlabs.openphotos.data.db

import android.net.Uri
import androidx.room.Embedded
import androidx.room.Relation
import com.qaxlabs.openphotos.data.BackupStatus
import com.qaxlabs.openphotos.data.MediaType
import com.qaxlabs.openphotos.data.UnifiedMediaItem

/**
 * Room relational wrapper combining core [MediaEntity] with its optional
 * local metadata, remote metadata, and backup task entries.
 */
data class UnifiedMediaRelation(
    @Embedded
    val media: MediaEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "mediaId",
    )
    val localMetadata: LocalMediaMetadataEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "mediaId",
    )
    val remoteMetadata: RemoteMediaMetadataEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "mediaId",
    )
    val backupTask: BackupTaskEntity?,
) {
    /**
     * Converts the database relation into a clean domain [UnifiedMediaItem].
     */
    fun toDomain(): UnifiedMediaItem {
        val type = runCatching { MediaType.valueOf(media.mediaType) }.getOrDefault(MediaType.IMAGE)
        val status = runCatching { BackupStatus.valueOf(media.backupStatus) }.getOrDefault(BackupStatus.LOCAL_ONLY)

        val parsedUri = localMetadata?.localUri?.let { uriStr ->
            runCatching { Uri.parse(uriStr) }.getOrNull()
        }

        return UnifiedMediaItem(
            id = media.id,
            filename = media.filename,
            mimeType = media.mimeType,
            mediaType = type,
            sizeBytes = media.sizeBytes,
            width = media.width,
            height = media.height,
            durationMs = media.durationMs,
            createdAt = media.createdAt,
            modifiedAt = media.modifiedAt,
            sha256 = media.sha256,
            favorite = media.favorite,
            trashState = media.trashState,
            backupStatus = status,
            localUri = parsedUri,
            devicePath = localMetadata?.devicePath,
            telegramChatId = remoteMetadata?.telegramChatId,
            telegramMessageId = remoteMetadata?.telegramMessageId,
            thumbnailRemoteId = remoteMetadata?.thumbnailRemoteId,
            uploadedAt = remoteMetadata?.uploadedAt,
        )
    }
}
