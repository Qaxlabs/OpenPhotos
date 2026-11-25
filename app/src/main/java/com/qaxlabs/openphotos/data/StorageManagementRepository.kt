package com.qaxlabs.openphotos.data

import android.content.Context
import android.util.Log
import com.qaxlabs.openphotos.data.db.LocalMediaMetadataEntity
import com.qaxlabs.openphotos.data.db.MediaEntity
import com.qaxlabs.openphotos.data.db.RemoteMediaMetadataEntity
import com.qaxlabs.openphotos.data.db.UnifiedMediaDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Statistics snapshot for the storage dashboard.
 */
data class StorageStats(
    val localCount: Int = 0,
    val cloudCount: Int = 0,
    val backedUpCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val totalLocalBytes: Long = 0L,
    val thumbnailCacheBytes: Long = 0L,
    val fullresCacheBytes: Long = 0L,
    val recoverableBytes: Long = 0L,
)

/**
 * Repository managing independent local/cloud deletion, space recovery, and storage analytics.
 */
@Singleton
class StorageManagementRepository @Inject constructor(
    private val client: TelegramClient,
    private val unifiedMediaDao: UnifiedMediaDao,
    private val indexRepository: IndexRepository,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "StorageManagementRepo"
    }

    /**
     * Deletes the local device file while preserving the Telegram cloud copy and remote index.
     */
    suspend fun deleteDeviceCopy(item: UnifiedMediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            // Delete local file if present on filesystem
            val path = item.devicePath
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }

            // Update database backupStatus to REMOTE_ONLY if backed up
            val relation = unifiedMediaDao.getById(item.id)
            if (relation != null && relation.remoteMetadata != null) {
                val updatedMedia = relation.media.copy(backupStatus = BackupStatus.REMOTE_ONLY.name)
                unifiedMediaDao.upsertUnifiedMedia(updatedMedia, null, relation.remoteMetadata)
            } else {
                unifiedMediaDao.deleteById(item.id)
            }

            Log.i(TAG, "Deleted local device copy for media id=${item.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete local copy: ${e.message}", e)
            false
        }
    }

    /**
     * Deletes the Telegram cloud copy while preserving the local file on device.
     */
    suspend fun deleteCloudCopy(item: UnifiedMediaItem): Boolean = withContext(Dispatchers.IO) {
        var cloudDeleted = false
        val chatId = item.telegramChatId
        val msgId = item.telegramMessageId

        if (chatId != null && msgId != null && msgId != 0L) {
            try {
                val req = TdApi.DeleteMessages(chatId, longArrayOf(msgId), true)
                client.send(req)
                cloudDeleted = true
                Log.i(TAG, "Deleted Telegram document message id=$msgId from channel $chatId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete Telegram message: ${e.message}", e)
            }
        }

        // Update Room database status
        val relation = unifiedMediaDao.getById(item.id)
        if (relation != null) {
            if (relation.localMetadata != null) {
                val updatedMedia = relation.media.copy(backupStatus = BackupStatus.LOCAL_ONLY.name)
                unifiedMediaDao.upsertUnifiedMedia(updatedMedia, relation.localMetadata, null)
            } else {
                unifiedMediaDao.deleteById(item.id)
            }
        }

        // Push index update
        indexRepository.pushIndex()
        cloudDeleted
    }

    /**
     * Deletes both local device file and Telegram cloud copy.
     */
    suspend fun deleteEverywhere(item: UnifiedMediaItem): Boolean = withContext(Dispatchers.IO) {
        val localSuccess = deleteDeviceCopy(item)
        val cloudSuccess = deleteCloudCopy(item)
        unifiedMediaDao.deleteById(item.id)
        indexRepository.pushIndex()
        localSuccess || cloudSuccess
    }

    /**
     * Identifies verified backed-up items and deletes local files to free up space.
     */
    suspend fun freeUpSpace(): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        val allRelations = unifiedMediaDao.getUnifiedMediaPaged(limit = 10_000, offset = 0)

        for (rel in allRelations) {
            val domain = rel.toDomain()
            // Verify item is backed up to Telegram and local file exists
            if (domain.backupStatus == BackupStatus.BACKED_UP &&
                domain.telegramMessageId != null &&
                !domain.devicePath.isNullOrEmpty()
            ) {
                val file = File(domain.devicePath)
                if (file.exists() && file.length() > 0) {
                    val size = file.length()
                    if (file.delete()) {
                        freedBytes += size
                        // Transition status to REMOTE_ONLY
                        val updatedMedia = rel.media.copy(backupStatus = BackupStatus.REMOTE_ONLY.name)
                        unifiedMediaDao.upsertUnifiedMedia(updatedMedia, null, rel.remoteMetadata)
                    }
                }
            }
        }

        Log.i(TAG, "Free Up Space completed. Recovered ${freedBytes / (1024 * 1024)} MB.")
        freedBytes
    }

    /**
     * Calculates storage statistics for the dashboard.
     */
    suspend fun calculateStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val relations = unifiedMediaDao.getUnifiedMediaPaged(limit = 10_000, offset = 0)
        var localCount = 0
        var cloudCount = 0
        var backedUpCount = 0
        var pendingCount = 0
        var failedCount = 0
        var totalLocalBytes = 0L
        var recoverableBytes = 0L

        for (rel in relations) {
            val status = rel.media.backupStatus
            val isLocal = rel.localMetadata != null
            val isRemote = rel.remoteMetadata != null

            if (isLocal) localCount++
            if (isRemote) cloudCount++

            when (status) {
                BackupStatus.BACKED_UP.name -> {
                    backedUpCount++
                    if (isLocal && isRemote) {
                        recoverableBytes += rel.media.sizeBytes
                    }
                }
                BackupStatus.PENDING_BACKUP.name, BackupStatus.UPLOADING.name -> pendingCount++
                BackupStatus.FAILED.name -> failedCount++
            }

            if (isLocal) {
                totalLocalBytes += rel.media.sizeBytes
            }
        }

        val thumbsDir = File(context.cacheDir, "vault_thumbnails")
        val fullresDir = File(context.cacheDir, "vault_fullres")

        val thumbBytes = thumbsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val fullresBytes = fullresDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

        StorageStats(
            localCount = localCount,
            cloudCount = cloudCount,
            backedUpCount = backedUpCount,
            pendingCount = pendingCount,
            failedCount = failedCount,
            totalLocalBytes = totalLocalBytes,
            thumbnailCacheBytes = thumbBytes,
            fullresCacheBytes = fullresBytes,
            recoverableBytes = recoverableBytes,
        )
    }
}
