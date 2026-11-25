package com.qaxlabs.openphotos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for UnifiedMediaItem mapping and BackupStatus logic.
 */
class UnifiedMediaRepositoryTest {

    @Test
    fun backupStatusEnumValues_containExpectedStates() {
        val statuses = BackupStatus.entries.map { it.name }
        assertTrue(statuses.contains("LOCAL_ONLY"))
        assertTrue(statuses.contains("PENDING_BACKUP"))
        assertTrue(statuses.contains("UPLOADING"))
        assertTrue(statuses.contains("BACKED_UP"))
        assertTrue(statuses.contains("REMOTE_ONLY"))
        assertTrue(statuses.contains("FAILED"))
        assertTrue(statuses.contains("OVERSIZED"))
    }

    @Test
    fun unifiedMediaItem_backedUpStateConstruction() {
        val item = UnifiedMediaItem(
            id = "media_101",
            filename = "IMG_001.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            sizeBytes = 2048576L,
            createdAt = 1700000000000L,
            sha256 = "sha256:1122334455",
            backupStatus = BackupStatus.BACKED_UP,
            telegramChatId = -1009999L,
            telegramMessageId = 12345L,
        )

        assertEquals("media_101", item.id)
        assertEquals(MediaType.IMAGE, item.mediaType)
        assertEquals(BackupStatus.BACKED_UP, item.backupStatus)
        assertEquals(-1009999L, item.telegramChatId)
        assertEquals(12345L, item.telegramMessageId)
    }
}
