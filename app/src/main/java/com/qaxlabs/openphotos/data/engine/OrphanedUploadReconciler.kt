package com.qaxlabs.openphotos.data.engine

import android.util.Log
import com.qaxlabs.openphotos.data.TelegramClient
import com.qaxlabs.openphotos.data.VaultChannelRepository
import com.qaxlabs.openphotos.data.VaultIndexV2Spec
import com.qaxlabs.openphotos.data.db.MediaEntity
import com.qaxlabs.openphotos.data.db.RemoteMediaMetadataEntity
import com.qaxlabs.openphotos.data.db.UnifiedMediaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles orphaned Telegram upload messages (uploads that succeeded to Telegram but failed to register in the local/remote index).
 */
@Singleton
class OrphanedUploadReconciler @Inject constructor(
    private val client: TelegramClient,
    private val vaultChannelRepository: VaultChannelRepository,
    private val unifiedMediaDao: UnifiedMediaDao,
) {
    companion object {
        private const val TAG = "OrphanedUploadReconciler"
    }

    suspend fun reconcileOrphanedUploads(): Int = withContext(Dispatchers.IO) {
        var reconciledCount = 0
        try {
            val chatId = vaultChannelRepository.getOrCreateVaultChannel()
            val historyReq = TdApi.GetChatHistory().also {
                it.chatId = chatId
                it.fromMessageId = 0L
                it.offset = 0
                it.limit = 50
                it.onlyLocal = false
            }

            val messagesResult = runCatching {
                client.send(historyReq) as TdApi.Messages
            }.getOrNull() ?: return@withContext 0

            for (msg in messagesResult.messages) {
                val content = msg.content as? TdApi.MessageDocument ?: continue
                val fileName = content.document.fileName

                // Skip index snapshots and journal files
                if (fileName == VaultIndexV2Spec.V1_INDEX_FILENAME ||
                    fileName == VaultIndexV2Spec.V2_INDEX_FILENAME ||
                    fileName.startsWith(VaultIndexV2Spec.JOURNAL_PREFIX)
                ) {
                    continue
                }

                // Check if message is already indexed in Room database
                val existingItem = unifiedMediaDao.getById("msg_${msg.id}")
                if (existingItem != null) continue

                // Recover orphaned file message
                val mediaId = "orphan_${msg.id}_${UUID.randomUUID().toString().take(8)}"
                val mimeType = content.document.mimeType.ifEmpty { "application/octet-stream" }
                val sizeBytes = content.document.document.size.toLong()
                val takenAt = msg.date * 1_000L
                val isVideo = mimeType.startsWith("video/")
                val remoteThumbId = content.document.thumbnail?.file?.remote?.id

                val mediaEntity = MediaEntity(
                    id = mediaId,
                    filename = fileName,
                    mimeType = mimeType,
                    mediaType = if (isVideo) "VIDEO" else "IMAGE",
                    sizeBytes = sizeBytes,
                    createdAt = takenAt,
                    modifiedAt = takenAt,
                    sha256 = "sha256:orphan_${msg.id}",
                    backupStatus = "REMOTE_ONLY",
                )

                val remoteMeta = RemoteMediaMetadataEntity(
                    mediaId = mediaId,
                    telegramChatId = chatId,
                    telegramMessageId = msg.id,
                    thumbnailRemoteId = remoteThumbId,
                    uploadedAt = takenAt,
                )

                unifiedMediaDao.upsertUnifiedMedia(mediaEntity, null, remoteMeta)
                reconciledCount++
                Log.i(TAG, "Reconciled orphaned document message id=${msg.id} file='$fileName'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconcile orphaned uploads: ${e.message}", e)
        }
        reconciledCount
    }
}
