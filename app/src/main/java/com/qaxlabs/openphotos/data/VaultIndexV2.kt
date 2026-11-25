package com.qaxlabs.openphotos.data

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * File names for Vault Index V2 snapshots and incremental journals.
 */
object VaultIndexV2Spec {
    const val V1_INDEX_FILENAME = "openphotos_index.json"
    const val V2_INDEX_FILENAME = "openphotos_index_v2.json"
    const val JOURNAL_PREFIX = "openphotos_journal_"
    const val INDEX_CAPTION_V2 = "OpenPhotos Vault Index V2 Snapshot"
    const val JOURNAL_CAPTION = "OpenPhotos Vault Index Journal Delta"
}

/**
 * Version 2 Index Snapshot document pinned inside the private vault channel.
 */
@Serializable
data class VaultIndexV2(
    val version: Int = 2,
    val sequenceNumber: Long = 1L,
    val createdAt: String = Instant.now().toString(),
    val files: List<VaultIndexEntryV2> = emptyList(),
    val albums: List<VaultIndexAlbumEntry> = emptyList(),
)

/**
 * Enhanced metadata record for an individual media item in Vault Index V2.
 */
@Serializable
data class VaultIndexEntryV2(
    val id: String,
    val messageId: Long,
    val filename: String,
    val sizeBytes: Long,
    val checksum: String,
    val takenAt: String,
    val uploadedAt: String,
    val mimeType: String,
    val localUri: String = "",
    val chatId: Long = 0L,
    val thumbnailRemoteId: String? = null,
    val favorite: Boolean = false,
    val trashState: Boolean = false,
    val albums: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
)

/**
 * Metadata record for a user album in Vault Index V2.
 */
@Serializable
data class VaultIndexAlbumEntry(
    val id: String,
    val title: String,
    val createdAt: String,
    val coverIndexId: String? = null,
)

/**
 * Incremental metadata delta entry representing small batch modifications (journaling).
 */
@Serializable
data class VaultIndexDelta(
    val version: Int = 2,
    val sequenceNumber: Long,
    val timestamp: String = Instant.now().toString(),
    val addedFiles: List<VaultIndexEntryV2> = emptyList(),
    val deletedFileIds: List<String> = emptyList(),
    val updatedFiles: List<VaultIndexEntryV2> = emptyList(),
)
