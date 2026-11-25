package com.qaxlabs.openphotos.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for legacy VaultIndex V1 to VaultIndexV2 serialization and migration.
 */
class VaultIndexV2MigrationTest {

    @Test
    fun legacyIndexV1_upgradesToVaultIndexV2() {
        val entryV1 = VaultIndexEntry(
            id = "idx_v1_001",
            messageId = 12345L,
            filename = "photo.jpg",
            sizeBytes = 1048576L,
            checksum = "sha256:abc123def",
            takenAt = "2026-03-30T10:00:00Z",
            uploadedAt = "2026-03-30T10:05:00Z",
            mimeType = "image/jpeg",
            localUri = "content://media/101",
            chatId = -1009999L,
            thumbnailRemoteId = "thumb_v1",
        )

        val v1Index = VaultIndex(version = 1, files = listOf(entryV1))
        val v1Json = Json.encodeToString(v1Index)

        val parsedV1 = Json.decodeFromString<VaultIndex>(v1Json)
        assertEquals(1, parsedV1.version)
        assertEquals(1, parsedV1.files.size)

        // Upgrade to V2 entry
        val entryV2 = VaultIndexEntryV2(
            id = parsedV1.files[0].id,
            messageId = parsedV1.files[0].messageId,
            filename = parsedV1.files[0].filename,
            sizeBytes = parsedV1.files[0].sizeBytes,
            checksum = parsedV1.files[0].checksum,
            takenAt = parsedV1.files[0].takenAt,
            uploadedAt = parsedV1.files[0].uploadedAt,
            mimeType = parsedV1.files[0].mimeType,
            localUri = parsedV1.files[0].localUri,
            chatId = parsedV1.files[0].chatId,
            thumbnailRemoteId = parsedV1.files[0].thumbnailRemoteId,
            favorite = false,
            trashState = false,
        )

        val v2Snapshot = VaultIndexV2(
            version = 2,
            sequenceNumber = 1L,
            files = listOf(entryV2),
        )

        val v2Json = Json.encodeToString(v2Snapshot)
        val parsedV2 = Json.decodeFromString<VaultIndexV2>(v2Json)

        assertEquals(2, parsedV2.version)
        assertEquals(1L, parsedV2.sequenceNumber)
        assertEquals("idx_v1_001", parsedV2.files[0].id)
        assertNotNull(parsedV2.files[0].checksum)
    }
}
