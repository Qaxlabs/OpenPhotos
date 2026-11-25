package com.qaxlabs.openphotos.data.engine

import android.content.Context
import android.util.Log
import com.qaxlabs.openphotos.data.FloodWaitState
import com.qaxlabs.openphotos.data.IndexRepository
import com.qaxlabs.openphotos.data.TelegramClient
import com.qaxlabs.openphotos.data.TelegramException
import com.qaxlabs.openphotos.data.VaultChannelRepository
import com.qaxlabs.openphotos.data.db.BackupTaskDao
import com.qaxlabs.openphotos.data.db.LocalMediaMetadataEntity
import com.qaxlabs.openphotos.data.db.MediaEntity
import com.qaxlabs.openphotos.data.db.RemoteMediaMetadataEntity
import com.qaxlabs.openphotos.data.db.UnifiedMediaDao
import com.qaxlabs.openphotos.data.util.ThumbnailGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_RETRIES = 5
private const val UPLOAD_TIMEOUT_MS = 30L * 60 * 1_000

@Singleton
class AdaptiveUploadScheduler @Inject constructor(
    private val client: TelegramClient,
    private val backupTaskDao: BackupTaskDao,
    private val unifiedMediaDao: UnifiedMediaDao,
    private val indexRepository: IndexRepository,
    private val vaultChannelRepository: VaultChannelRepository,
    private val constraintChecker: DeviceConstraintChecker,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AdaptiveUploadScheduler"
        private const val BATCH_SIZE = 20
    }

    private val _floodWaitState = MutableStateFlow<FloodWaitState>(FloodWaitState.None)
    val floodWaitState: StateFlow<FloodWaitState> = _floodWaitState.asStateFlow()

    /**
     * Executes queued backup tasks sequentially in adaptive batches.
     */
    suspend fun executePendingTasks(): Int = withContext(Dispatchers.IO) {
        if (!constraintChecker.canExecuteBackup()) {
            Log.d(TAG, "Backup constraints not satisfied (network/battery). Pausing scheduler.")
            return@withContext 0
        }

        val now = System.currentTimeMillis()
        val pendingTasks = backupTaskDao.getPendingTasks(now, BATCH_SIZE)
        if (pendingTasks.isEmpty()) return@withContext 0

        var executedCount = 0
        for (task in pendingTasks) {
            if (!constraintChecker.canExecuteBackup()) break

            val success = executeSingleTask(task)
            if (success) {
                executedCount++
                // Adaptive delay between document uploads (500ms - 1500ms)
                delay(800L)
            } else {
                // Task failed or hit flood wait -> pause batch
                break
            }
        }

        executedCount
    }

    private suspend fun executeSingleTask(task: com.qaxlabs.openphotos.data.db.BackupTaskEntity): Boolean {
        val relation = unifiedMediaDao.getById(task.mediaId)
        if (relation == null) {
            backupTaskDao.updateTaskState(task.taskId, "FAILED", lastError = "Media not found")
            updateSessionProgressForTask(task.sessionId)
            return false
        }

        // Crash recovery check: if remote metadata already exists, complete task directly
        if (relation.remoteMetadata != null && relation.remoteMetadata.telegramMessageId != 0L) {
            backupTaskDao.updateTaskState(task.taskId, "COMPLETED")
            updateSessionProgressForTask(task.sessionId)
            return true
        }

        val localPath = relation.localMetadata?.devicePath
        if (localPath.isNullOrEmpty() || !File(localPath).exists()) {
            backupTaskDao.updateTaskState(task.taskId, "FAILED", lastError = "Local file missing")
            updateSessionProgressForTask(task.sessionId)
            return false
        }

        backupTaskDao.updateTaskState(task.taskId, "UPLOADING")

        try {
            val sha256 = computeSha256(localPath)
            val chatId = vaultChannelRepository.getOrCreateVaultChannel()

            val thumbInfo = ThumbnailGenerator.generateThumbnail(
                context,
                com.qaxlabs.openphotos.data.MediaItem(
                    id = 0L,
                    uri = android.net.Uri.parse(relation.localMetadata.localUri),
                    displayName = relation.media.filename,
                    sizeBytes = relation.media.sizeBytes,
                    mimeType = relation.media.mimeType,
                    dateAdded = relation.media.createdAt / 1000L,
                    absolutePath = localPath,
                )
            )

            val inputThumbnail = thumbInfo?.let {
                TdApi.InputThumbnail(TdApi.InputFileLocal(it.file.absolutePath), it.width, it.height)
            }

            val inputContent = TdApi.InputMessageDocument(
                TdApi.InputFileLocal(localPath),
                inputThumbnail,
                true,
                null,
            )

            val tempMsg = client.send(TdApi.SendMessage().also {
                it.chatId = chatId
                it.inputMessageContent = inputContent
            }) as TdApi.Message

            val tempMsgId = tempMsg.id

            val successUpdate = try {
                withTimeout(UPLOAD_TIMEOUT_MS) {
                    client.updates
                        .filterIsInstance<TdApi.UpdateMessageSendSucceeded>()
                        .first { it.oldMessageId == tempMsgId }
                }
            } finally {
                thumbInfo?.file?.delete()
            }

            val realMsgId = successUpdate.message.id
            val docContent = successUpdate.message.content as? TdApi.MessageDocument
            val remoteThumbId = docContent?.document?.thumbnail?.file?.remote?.id ?: ""

            // Persist to unified media DB
            val updatedMedia = relation.media.copy(backupStatus = "BACKED_UP", sha256 = sha256)
            val remoteMeta = RemoteMediaMetadataEntity(
                mediaId = relation.media.id,
                telegramChatId = chatId,
                telegramMessageId = realMsgId,
                thumbnailRemoteId = remoteThumbId,
                uploadedAt = System.currentTimeMillis(),
            )
            unifiedMediaDao.upsertUnifiedMedia(updatedMedia, relation.localMetadata, remoteMeta)

            // Push updated index to Telegram
            indexRepository.pushIndex()

            backupTaskDao.updateTaskState(task.taskId, "COMPLETED")
            updateSessionProgressForTask(task.sessionId)
            return true

        } catch (e: TelegramException) {
            if (e.code == 429) {
                val waitSeconds = parseWaitSeconds(e.message ?: "")
                val resumeAt = System.currentTimeMillis() + waitSeconds * 1000L
                _floodWaitState.value = FloodWaitState.Waiting(resumeAt)

                backupTaskDao.updateTaskState(
                    taskId = task.taskId,
                    state = "FLOOD_WAIT",
                    lastError = "FLOOD_WAIT $waitSeconds seconds",
                    nextAttemptTimestamp = resumeAt,
                    retryCount = task.retryCount + 1,
                )
                return false
            } else if (e.code == 401 || e.code == 403) {
                backupTaskDao.updateTaskState(task.taskId, "ACCOUNT_RESTRICTED", lastError = e.message)
                return false
            } else {
                val backoffSec = (1 shl task.retryCount) * 5L
                backupTaskDao.updateTaskState(
                    taskId = task.taskId,
                    state = "FAILED",
                    lastError = e.message,
                    nextAttemptTimestamp = System.currentTimeMillis() + backoffSec * 1000L,
                    retryCount = task.retryCount + 1,
                )
                return false
            }
        } catch (e: Exception) {
            backupTaskDao.updateTaskState(task.taskId, "FAILED", lastError = e.message ?: "Unknown error")
            updateSessionProgressForTask(task.sessionId)
            return false
        }
    }

    private suspend fun updateSessionProgressForTask(sessionId: String) {
        val tasks = backupTaskDao.getTasksForSession(sessionId)
        val completedCount = tasks.count { it.state == "COMPLETED" }
        val failedCount = tasks.count { it.state == "FAILED" || it.state == "ACCOUNT_RESTRICTED" }
        val uploadedBytes = tasks.filter { it.state == "COMPLETED" }.sumOf { it.totalBytes }
        backupTaskDao.updateSessionProgress(sessionId, completedCount, failedCount, uploadedBytes)

        if (completedCount == tasks.size && tasks.isNotEmpty()) {
            backupTaskDao.updateSessionStatus(sessionId, "COMPLETED")
        }
    }

    private fun parseWaitSeconds(msg: String): Long {
        val match = Regex("FLOOD_WAIT_(\\d+)").find(msg)
            ?: Regex("retry after (\\d+)").find(msg)
            ?: Regex("(\\d+)").find(msg)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 60L
    }

    private fun computeSha256(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65_536)
        File(path).inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }
}
