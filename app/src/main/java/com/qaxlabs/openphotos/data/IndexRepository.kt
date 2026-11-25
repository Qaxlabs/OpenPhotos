package com.qaxlabs.openphotos.data

import android.content.Context
import android.util.Log
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import com.qaxlabs.openphotos.data.db.UploadedItemEntity
import com.qaxlabs.openphotos.data.di.ApplicationScope
import com.qaxlabs.openphotos.data.engine.OrphanedUploadReconciler
import com.qaxlabs.openphotos.data.engine.VaultIndexJournalEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.drinkless.tdlib.TdApi
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Done(val itemCount: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

@Singleton
class IndexRepository @Inject constructor(
    private val client: TelegramClient,
    private val dao: UploadedItemDao,
    private val unifiedMediaDao: com.qaxlabs.openphotos.data.db.UnifiedMediaDao,
    private val secureStore: SecureStore,
    private val vaultChannelRepository: VaultChannelRepository,
    private val journalEngine: VaultIndexJournalEngine,
    private val orphanReconciler: OrphanedUploadReconciler,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "IndexRepository"
        private const val DOWNLOAD_TIMEOUT_MS = 30_000L
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val pushMutex = Mutex()

    /**
     * Pulls the remote index from the vault channel and reconciles it into Room.
     * Supports V1 to V2 safe migration and orphan reconciliation.
     */
    suspend fun syncFromRemote() {
        _syncState.value = SyncState.Syncing
        try {
            val chatId = vaultChannelRepository.getOrCreateVaultChannel()
            val indexMessage = findIndexMessage(chatId)

            if (indexMessage != null) {
                val jsonText = downloadDocumentText(indexMessage)

                val index = Json.decodeFromString<VaultIndex>(jsonText)

                dao.clearAll()
                unifiedMediaDao.clearAll()

                val legacyEntities = index.files.map { entry -> entry.toEntity(chatId) }
                dao.insertAll(legacyEntities)

                for (entry in index.files) {
                    val entity = entry.toEntity(chatId)
                    val isVideo = entity.mimeType.startsWith("video/")
                    val mediaEntity = com.qaxlabs.openphotos.data.db.MediaEntity(
                        id = entity.indexId,
                        filename = entity.displayName,
                        mimeType = entity.mimeType,
                        mediaType = if (isVideo) "VIDEO" else "IMAGE",
                        sizeBytes = entity.sizeBytes,
                        createdAt = entity.takenAt,
                        modifiedAt = entity.uploadedAt,
                        sha256 = entity.sha256,
                        backupStatus = "REMOTE_ONLY",
                    )
                    val remoteMeta = com.qaxlabs.openphotos.data.db.RemoteMediaMetadataEntity(
                        mediaId = entity.indexId,
                        telegramChatId = entity.telegramChatId,
                        telegramMessageId = entity.telegramMessageId,
                        thumbnailRemoteId = entity.thumbnailRemoteId,
                        uploadedAt = entity.uploadedAt,
                    )
                    val localMeta = if (entity.localUri.isNotBlank()) {
                        com.qaxlabs.openphotos.data.db.LocalMediaMetadataEntity(
                            mediaId = entity.indexId,
                            localUri = entity.localUri,
                            devicePath = null,
                            dateAdded = entity.takenAt,
                        )
                    } else null

                    unifiedMediaDao.upsertUnifiedMedia(mediaEntity, localMeta, remoteMeta)
                }

                // Run orphan reconciler to discover untracked uploads
                val orphansCount = orphanReconciler.reconcileOrphanedUploads()
                if (orphansCount > 0) {
                    Log.i(TAG, "Reconciled $orphansCount orphaned uploads from Telegram channel.")
                }

                secureStore.setIndexMessageId(indexMessage.id)

                val totalCount = index.files.size + orphansCount
                Log.i(TAG, "Synced $totalCount items from remote index (including $orphansCount orphans)")
                _syncState.value = SyncState.Done(totalCount)
            } else {
                Log.i(TAG, "No remote index found — fresh account")
                _syncState.value = SyncState.Done(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncFromRemote failed", e)
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
        }
    }

    /**
     * Builds full V2 snapshot or journal delta from Room and writes it to Telegram.
     */
    suspend fun pushIndex() {
        pushMutex.withLock {
            try {
                val chatId = vaultChannelRepository.getOrCreateVaultChannel()
                val relations = unifiedMediaDao.getUnifiedMediaPaged(limit = 10_000, offset = 0)

                if (journalEngine.shouldCompacting() || secureStore.getIndexMessageId() == null) {
                    journalEngine.createAndPushSnapshot(relations)
                } else {
                    val entries = relations.map { rel ->
                        VaultIndexEntryV2(
                            id = rel.media.id,
                            messageId = rel.remoteMetadata?.telegramMessageId ?: 0L,
                            filename = rel.media.filename,
                            sizeBytes = rel.media.sizeBytes,
                            checksum = rel.media.sha256,
                            takenAt = Instant.ofEpochMilli(rel.media.createdAt).toString(),
                            uploadedAt = Instant.ofEpochMilli(rel.remoteMetadata?.uploadedAt ?: rel.media.modifiedAt).toString(),
                            mimeType = rel.media.mimeType,
                            localUri = rel.localMetadata?.localUri ?: "",
                            chatId = rel.remoteMetadata?.telegramChatId ?: chatId,
                            thumbnailRemoteId = rel.remoteMetadata?.thumbnailRemoteId,
                            favorite = rel.media.favorite,
                            trashState = rel.media.trashState,
                            albums = emptyList(),
                            width = rel.media.width,
                            height = rel.media.height,
                            durationMs = rel.media.durationMs,
                        )
                    }
                    journalEngine.appendDelta(added = entries)
                }
            } catch (e: Exception) {
                Log.e(TAG, "pushIndex failed (non-fatal)", e)
            }
        }
    }

    private suspend fun findIndexMessage(chatId: Long): TdApi.Message? {
        val cachedId = secureStore.getIndexMessageId()
        if (cachedId != null) {
            val message = runCatching {
                client.send(TdApi.GetMessage(chatId, cachedId)) as TdApi.Message
            }.getOrNull()
            if (message != null && message.content is TdApi.MessageDocument) {
                return message
            }
            secureStore.clearIndexMessageId()
        }
        return scanHistoryForIndex(chatId)
    }

    private suspend fun scanHistoryForIndex(chatId: Long): TdApi.Message? {
        var fromMessageId = 0L
        repeat(2) {
            val historyResult = runCatching {
                val req = TdApi.GetChatHistory().also { r ->
                    r.chatId        = chatId
                    r.fromMessageId = fromMessageId
                    r.offset        = 0
                    r.limit         = 100
                    r.onlyLocal     = false
                }
                client.send(req) as TdApi.Messages
            }.getOrNull() ?: return null

            for (msg in historyResult.messages) {
                val content = msg.content
                if (content is TdApi.MessageDocument &&
                    (content.document.fileName == VaultIndex.INDEX_FILENAME ||
                     content.document.fileName == VaultIndexV2Spec.V2_INDEX_FILENAME)) {
                    Log.i(TAG, "Found index message via scan: ${msg.id}")
                    return msg
                }
            }

            if (historyResult.messages.isEmpty()) return null
            fromMessageId = historyResult.messages.last().id
        }
        return null
    }

    private suspend fun downloadDocumentText(message: TdApi.Message): String {
        val doc  = (message.content as TdApi.MessageDocument).document
        val file = doc.document

        if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) {
            return File(file.local.path).readText()
        }

        client.send(TdApi.DownloadFile(file.id, 32, 0, 0, false))

        val completed = withTimeout(DOWNLOAD_TIMEOUT_MS) {
            client.updates
                .filterIsInstance<TdApi.UpdateFile>()
                .filter { it.file.id == file.id && it.file.local.isDownloadingCompleted }
                .first()
        }
        return File(completed.file.local.path).readText()
    }
}

/** Converts a JSON index entry back into a Room entity during [IndexRepository.syncFromRemote]. */
fun VaultIndexEntry.toEntity(chatId: Long): UploadedItemEntity = UploadedItemEntity(
    indexId           = id,
    localUri          = localUri,
    displayName       = filename,
    sizeBytes         = sizeBytes,
    mimeType          = mimeType,
    sha256            = checksum,
    takenAt           = runCatching { Instant.parse(takenAt).toEpochMilli() }.getOrDefault(0L),
    telegramChatId    = if (this.chatId != 0L) this.chatId else chatId,
    telegramMessageId = messageId,
    uploadedAt        = runCatching { Instant.parse(uploadedAt).toEpochMilli() }.getOrDefault(0L),
    thumbnailRemoteId = thumbnailRemoteId,
)

/** Converts a Room entity to a JSON index entry for [IndexRepository.pushIndex]. */
fun UploadedItemEntity.toIndexEntry(): VaultIndexEntry = VaultIndexEntry(
    id                = indexId,
    messageId         = telegramMessageId,
    filename          = displayName,
    sizeBytes         = sizeBytes,
    checksum          = sha256,
    takenAt           = Instant.ofEpochMilli(takenAt).toString(),
    uploadedAt        = Instant.ofEpochMilli(uploadedAt).toString(),
    mimeType          = mimeType,
    localUri          = localUri,
    chatId            = telegramChatId,
    thumbnailRemoteId = thumbnailRemoteId,
)
