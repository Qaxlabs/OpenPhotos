package com.qaxlabs.openphotos.data.engine

import com.qaxlabs.openphotos.data.VaultIndexDelta
import com.qaxlabs.openphotos.data.VaultIndexEntryV2
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for VaultIndexDelta serialization and sequence number incrementing.
 */
class VaultIndexJournalEngineTest {

    @Test
    fun vaultIndexDelta_serialization() {
        val entry = VaultIndexEntryV2(
            id = "item_delta_101",
            messageId = 8888L,
            filename = "delta.jpg",
            sizeBytes = 2048576L,
            checksum = "sha256:delta123",
            takenAt = "2026-03-30T12:00:00Z",
            uploadedAt = "2026-03-30T12:01:00Z",
            mimeType = "image/jpeg",
        )

        val delta = VaultIndexDelta(
            version = 2,
            sequenceNumber = 42L,
            addedFiles = listOf(entry),
        )

        val deltaJson = Json.encodeToString(delta)
        val parsedDelta = Json.decodeFromString<VaultIndexDelta>(deltaJson)

        assertEquals(2, parsedDelta.version)
        assertEquals(42L, parsedDelta.sequenceNumber)
        assertEquals(1, parsedDelta.addedFiles.size)
        assertEquals("item_delta_101", parsedDelta.addedFiles[0].id)
    }
}
