package com.qaxlabs.openphotos.data

import android.content.Context
import android.util.Log
import com.qaxlabs.openphotos.data.db.LocalMediaMetadataEntity
import com.qaxlabs.openphotos.data.db.MediaEntity
import com.qaxlabs.openphotos.data.db.RemoteMediaMetadataEntity
import com.qaxlabs.openphotos.data.db.UnifiedMediaDao
import com.qaxlabs.openphotos.data.db.UnifiedMediaRelation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth repository for querying and managing the unified local
 * and Telegram cloud media library.
 */
@Singleton
class UnifiedMediaRepository @Inject constructor(
    private val dao: UnifiedMediaDao,
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "UnifiedMediaRepository"
    }

    /**
     * Observes all media items in the unified database ordered by creation date descending.
     */
    fun observeAllUnifiedMedia(): Flow<List<UnifiedMediaItem>> {
        return dao.observeAllUnifiedMedia().map { relations ->
            relations.map { it.toDomain() }
        }
    }

    /**
     * Retrieves a paginated snapshot of unified media items.
     * Merges local device MediaStore entries with cloud database records.
     */
    suspend fun getUnifiedMediaPaged(limit: Int, offset: Int): List<UnifiedMediaItem> = withContext(Dispatchers.IO) {
        // 1. Load Room relations
        val roomRelations = dao.getUnifiedMediaPaged(limit, offset)
        val roomItems = roomRelations.map { it.toDomain() }

        // 2. Fetch local MediaStore items if accessible
        val localMediaList = runCatching { mediaRepository.queryAll() }.getOrDefault(emptyList())

        // 3. Map of known SHA-256 hashes in Room
        val sha256ToRoomItem = roomItems.associateBy { it.sha256 }

        val resultList = mutableListOf<UnifiedMediaItem>()
        val processedSha256s = mutableSetOf<String>()

        // 4. Process local device media items
        for (localItem in localMediaList) {
            val localPath = localItem.absolutePath ?: ""
            val sha256 = "sha256:local_${localItem.id}_${localItem.sizeBytes}_${localItem.dateAdded}"
            processedSha256s.add(sha256)

            val matchedRoomItem = sha256ToRoomItem[sha256]
            if (matchedRoomItem != null) {
                // Media exists both locally and in Telegram remote index
                resultList.add(
                    matchedRoomItem.copy(
                        localUri = localItem.uri,
                        devicePath = localPath,
                        backupStatus = if (matchedRoomItem.telegramMessageId != null)
                            BackupStatus.BACKED_UP
                        else
                            matchedRoomItem.backupStatus,
                    )
                )
            } else {
                // Local-only media item
                val type = if (localItem.mimeType.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE
                resultList.add(
                    UnifiedMediaItem(
                        id = "local_${localItem.id}",
                        filename = localItem.displayName,
                        mimeType = localItem.mimeType,
                        mediaType = type,
                        sizeBytes = localItem.sizeBytes,
                        createdAt = localItem.dateAdded * 1_000L,
                        modifiedAt = System.currentTimeMillis(),
                        sha256 = sha256,
                        backupStatus = BackupStatus.LOCAL_ONLY,
                        localUri = localItem.uri,
                        devicePath = localPath,
                    )
                )
            }
        }

        // 5. Add remote-only items from Room that were not present on local device
        for (roomItem in roomItems) {
            if (roomItem.sha256 !in processedSha256s) {
                val isLocalFileExisting = roomItem.devicePath?.let { File(it).exists() } == true
                val status = if (isLocalFileExisting) {
                    if (roomItem.telegramMessageId != null) BackupStatus.BACKED_UP else BackupStatus.LOCAL_ONLY
                } else {
                    if (roomItem.telegramMessageId != null) BackupStatus.REMOTE_ONLY else BackupStatus.LOCAL_ONLY
                }
                resultList.add(roomItem.copy(backupStatus = status))
            }
        }

        // Sort merged library chronologically newest-first
        resultList.sortByDescending { it.createdAt }
        resultList
    }

    /**
     * Persists a unified media item into the database tables.
     */
    suspend fun saveUnifiedMediaItem(item: UnifiedMediaItem) = withContext(Dispatchers.IO) {
        val mediaEntity = MediaEntity(
            id = item.id,
            filename = item.filename,
            mimeType = item.mimeType,
            mediaType = item.mediaType.name,
            sizeBytes = item.sizeBytes,
            width = item.width,
            height = item.height,
            durationMs = item.durationMs,
            createdAt = item.createdAt,
            modifiedAt = item.modifiedAt,
            sha256 = item.sha256,
            favorite = item.favorite,
            trashState = item.trashState,
            backupStatus = item.backupStatus.name,
        )

        val localMeta = item.localUri?.let {
            LocalMediaMetadataEntity(
                mediaId = item.id,
                localUri = it.toString(),
                devicePath = item.devicePath,
                dateAdded = item.createdAt,
            )
        }

        val remoteMeta = if (item.telegramChatId != null && item.telegramMessageId != null) {
            RemoteMediaMetadataEntity(
                mediaId = item.id,
                telegramChatId = item.telegramChatId,
                telegramMessageId = item.telegramMessageId,
                thumbnailRemoteId = item.thumbnailRemoteId,
                uploadedAt = item.uploadedAt ?: System.currentTimeMillis(),
            )
        } else null

        dao.upsertUnifiedMedia(mediaEntity, localMeta, remoteMeta)
    }

    private fun computeSha256(path: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65_536)
            File(path).inputStream().use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute sha256 for $path: ${e.message}")
            "sha256:unknown_${path.hashCode()}"
        }
    }
}
