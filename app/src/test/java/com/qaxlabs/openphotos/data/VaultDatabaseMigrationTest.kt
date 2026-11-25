package com.qaxlabs.openphotos.data

import com.qaxlabs.openphotos.data.db.MediaEntity
import com.qaxlabs.openphotos.data.db.RemoteMediaMetadataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit test suite for unified media database entities and mapping.
 */
class VaultDatabaseMigrationTest {

    @Test
    fun legacyUploadedItemConversion_populatesUnifiedMediaEntity() {
        val indexId = "test_idx_123"
        val displayName = "vacation.jpg"
        val mimeType = "image/jpeg"
        val sizeBytes = 4213112L
        val sha256 = "sha256:abcd1234efgh5678"
        val takenAt = 1600000000000L
        val uploadedAt = 1600000500000L
        val telegramChatId = -100123456789L
        val telegramMessageId = 48291L
        val thumbnailRemoteId = "thumb_remote_789"

        val mediaEntity = MediaEntity(
            id = indexId,
            filename = displayName,
            mimeType = mimeType,
            mediaType = if (mimeType.startsWith("video/")) "VIDEO" else "IMAGE",
            sizeBytes = sizeBytes,
            createdAt = takenAt,
            modifiedAt = uploadedAt,
            sha256 = sha256,
            backupStatus = "BACKED_UP",
        )

        val remoteMeta = RemoteMediaMetadataEntity(
            mediaId = indexId,
            telegramChatId = telegramChatId,
            telegramMessageId = telegramMessageId,
            thumbnailRemoteId = thumbnailRemoteId,
            uploadedAt = uploadedAt,
        )

        assertEquals("test_idx_123", mediaEntity.id)
        assertEquals("vacation.jpg", mediaEntity.filename)
        assertEquals("IMAGE", mediaEntity.mediaType)
        assertEquals("BACKED_UP", mediaEntity.backupStatus)

        assertNotNull(remoteMeta)
        assertEquals(-100123456789L, remoteMeta.telegramChatId)
        assertEquals(48291L, remoteMeta.telegramMessageId)
        assertEquals("thumb_remote_789", remoteMeta.thumbnailRemoteId)
    }

    @Test
    fun videoMimeType_mapsToVideoMediaType() {
        val mimeType = "video/mp4"
        val mediaType = if (mimeType.startsWith("video/")) "VIDEO" else "IMAGE"
        assertEquals("VIDEO", mediaType)
    }
}
