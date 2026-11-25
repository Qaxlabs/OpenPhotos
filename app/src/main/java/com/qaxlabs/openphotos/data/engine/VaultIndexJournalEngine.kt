package com.qaxlabs.openphotos.data.engine

import android.content.Context
import android.util.Log
import com.qaxlabs.openphotos.data.SecureStore
import com.qaxlabs.openphotos.data.TelegramClient
import com.qaxlabs.openphotos.data.VaultChannelRepository
import com.qaxlabs.openphotos.data.VaultIndexDelta
import com.qaxlabs.openphotos.data.VaultIndexEntryV2
import com.qaxlabs.openphotos.data.VaultIndexV2
import com.qaxlabs.openphotos.data.VaultIndexV2Spec
import com.qaxlabs.openphotos.data.db.UnifiedMediaRelation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.drinkless.tdlib.TdApi
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val PUSH_TIMEOUT_MS = 60_000L

@Singleton
class VaultIndexJournalEngine @Inject constructor(
    private val client: TelegramClient,
    private val vaultChannelRepository: VaultChannelRepository,
    private val secureStore: SecureStore,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "VaultIndexJournalEngine"
        private const val COMPACTION_THRESHOLD = 50
    }

    private val journalMutex = Mutex()
    private var deltaCountSinceSnapshot = 0

    /**
     * Appends an incremental delta journal entry to Telegram without rewriting the full snapshot.
     */
    suspend fun appendDelta(
        added: List<VaultIndexEntryV2> = emptyList(),
        deletedIds: List<String> = emptyList(),
        updated: List<VaultIndexEntryV2> = emptyList(),
    ): Boolean = journalMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val chatId = vaultChannelRepository.getOrCreateVaultChannel()
                val nextSeq = (secureStore.getIndexMessageId() ?: 100L) + 1L

                val delta = VaultIndexDelta(
                    version = 2,
                    sequenceNumber = nextSeq,
                    timestamp = Instant.now().toString(),
                    addedFiles = added,
                    deletedFileIds = deletedIds,
                    updatedFiles = updated,
                )

                val jsonText = Json.encodeToString(delta)
                val tmpFile = File(context.cacheDir, "${VaultIndexV2Spec.JOURNAL_PREFIX}$nextSeq.json")
                tmpFile.writeText(jsonText)

                val inputDoc = TdApi.InputMessageDocument(
                    TdApi.InputFileLocal(tmpFile.absolutePath),
                    null,
                    true,
                    TdApi.FormattedText(VaultIndexV2Spec.JOURNAL_CAPTION, emptyArray()),
                )

                val sendReq = TdApi.SendMessage().also {
                    it.chatId = chatId
                    it.inputMessageContent = inputDoc
                }

                val tempMessage = client.send(sendReq) as TdApi.Message
                withTimeout(PUSH_TIMEOUT_MS) {
                    client.updates
                        .filterIsInstance<TdApi.UpdateMessageSendSucceeded>()
                        .first { it.oldMessageId == tempMessage.id }
                }

                tmpFile.delete()
                deltaCountSinceSnapshot++
                Log.d(TAG, "Appended journal delta seq=$nextSeq with ${added.size} added files.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "appendDelta failed (non-fatal): ${e.message}", e)
                false
            }
        }
    }

    /**
     * Creates a full consolidated [VaultIndexV2] snapshot document and uploads/edits it in Telegram.
     */
    suspend fun createAndPushSnapshot(relations: List<UnifiedMediaRelation>): VaultIndexV2? = journalMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val chatId = vaultChannelRepository.getOrCreateVaultChannel()
                val nextSeq = (secureStore.getIndexMessageId() ?: 100L) + 1L

                val entries = relations.map { rel ->
                    val isVideo = rel.media.mimeType.startsWith("video/")
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

                val snapshot = VaultIndexV2(
                    version = 2,
                    sequenceNumber = nextSeq,
                    createdAt = Instant.now().toString(),
                    files = entries,
                    albums = emptyList(),
                )

                val jsonText = Json.encodeToString(snapshot)
                val tmpFile = File(context.cacheDir, VaultIndexV2Spec.V2_INDEX_FILENAME)
                tmpFile.writeText(jsonText)

                val inputDoc = TdApi.InputMessageDocument(
                    TdApi.InputFileLocal(tmpFile.absolutePath),
                    null,
                    true,
                    TdApi.FormattedText(VaultIndexV2Spec.INDEX_CAPTION_V2, emptyArray()),
                )

                val cachedMsgId = secureStore.getIndexMessageId()
                if (cachedMsgId != null) {
                    try {
                        val editReq = TdApi.EditMessageMedia().also {
                            it.chatId = chatId
                            it.messageId = cachedMsgId
                            it.inputMessageContent = inputDoc
                        }
                        client.send(editReq)
                        Log.d(TAG, "Edited V2 snapshot message $cachedMsgId")
                    } catch (_: Exception) {
                        secureStore.clearIndexMessageId()
                        sendNewSnapshotMessage(chatId, inputDoc)
                    }
                } else {
                    sendNewSnapshotMessage(chatId, inputDoc)
                }

                deltaCountSinceSnapshot = 0
                snapshot
            } catch (e: Exception) {
                Log.e(TAG, "createAndPushSnapshot failed: ${e.message}", e)
                null
            }
        }
    }

    private suspend fun sendNewSnapshotMessage(chatId: Long, inputDoc: TdApi.InputMessageDocument) {
        val sendReq = TdApi.SendMessage().also {
            it.chatId = chatId
            it.inputMessageContent = inputDoc
        }
        val tempMsg = client.send(sendReq) as TdApi.Message
        val success = withTimeout(PUSH_TIMEOUT_MS) {
            client.updates
                .filterIsInstance<TdApi.UpdateMessageSendSucceeded>()
                .first { it.oldMessageId == tempMsg.id }
        }
        val permId = success.message.id
        secureStore.setIndexMessageId(permId)
        Log.i(TAG, "Sent new V2 index snapshot message permanent ID = $permId")
    }

    fun shouldCompacting(): Boolean = deltaCountSinceSnapshot >= COMPACTION_THRESHOLD
}
