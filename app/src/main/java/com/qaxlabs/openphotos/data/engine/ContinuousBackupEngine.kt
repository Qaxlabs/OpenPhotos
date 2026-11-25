package com.qaxlabs.openphotos.data.engine

import android.util.Log
import com.qaxlabs.openphotos.data.BackupPreferences
import com.qaxlabs.openphotos.data.MediaItem
import com.qaxlabs.openphotos.data.MediaRepository
import com.qaxlabs.openphotos.data.db.BackupSessionEntity
import com.qaxlabs.openphotos.data.db.BackupTaskDao
import com.qaxlabs.openphotos.data.db.BackupTaskEntity
import com.qaxlabs.openphotos.data.db.LocalMediaMetadataEntity
import com.qaxlabs.openphotos.data.db.MediaEntity
import com.qaxlabs.openphotos.data.db.UnifiedMediaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinuousBackupEngine @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val backupPreferences: BackupPreferences,
    private val backupTaskDao: BackupTaskDao,
    private val unifiedMediaDao: UnifiedMediaDao,
    private val priorityManager: PriorityTaskQueueManager,
    private val scheduler: AdaptiveUploadScheduler,
) {
    companion object {
        private const val TAG = "ContinuousBackupEngine"
    }

    /**
     * Scans for newly created photos/videos since the last scan watermark,
     * filters by user preference, prevents duplicates, and queues P1 tasks.
     */
    suspend fun processContinuousMediaChanges(): Int = withContext(Dispatchers.IO) {
        if (!backupPreferences.isAutoBackupEnabled()) {
            Log.d(TAG, "Continuous backup is disabled in settings.")
            return@withContext 0
        }

        val photosEnabled = backupPreferences.isPhotosBackupEnabled()
        val videosEnabled = backupPreferences.isVideosBackupEnabled()
        val lastSeenWatermark = backupPreferences.getLastSeenTimestamp()

        val allMedia = mediaRepository.queryAll()
        val newMediaItems = allMedia.filter { media ->
            val isNewer = media.dateAdded > lastSeenWatermark
            val isAllowedType = (media.isImage && photosEnabled) || (media.isVideo && videosEnabled)
            isNewer && isAllowedType
        }

        if (newMediaItems.isEmpty()) {
            Log.d(TAG, "No new media files found since watermark $lastSeenWatermark.")
            return@withContext 0
        }

        var activeSession = backupTaskDao.getActiveSession()
        if (activeSession == null) {
            val sessionId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            activeSession = BackupSessionEntity(
                sessionId = sessionId,
                sessionType = "CONTINUOUS",
                createdAt = now,
                status = "ACTIVE",
                totalItems = newMediaItems.size,
                totalBytes = newMediaItems.sumOf { it.sizeBytes },
            )
            backupTaskDao.insertSession(activeSession)
        }

        val newTasks = mutableListOf<BackupTaskEntity>()
        var latestWatermark = lastSeenWatermark

        for (item in newMediaItems) {
            val mediaId = "local_${item.id}"
            val existingTask = backupTaskDao.getTaskById("task_$mediaId")
            if (existingTask != null) continue

            val pseudoSha256 = "sha256:local_${item.id}_${item.sizeBytes}_${item.dateAdded}"
            val existingMedia = unifiedMediaDao.findBySha256(pseudoSha256)
            if (existingMedia?.backupStatus == "BACKED_UP") continue

            val now = System.currentTimeMillis()
            val priority = priorityManager.calculatePriority(item, isUserSelected = false, now = now)

            val mediaEntity = MediaEntity(
                id = mediaId,
                filename = item.displayName,
                mimeType = item.mimeType,
                mediaType = if (item.isVideo) "VIDEO" else "IMAGE",
                sizeBytes = item.sizeBytes,
                createdAt = item.dateAdded * 1_000L,
                modifiedAt = now,
                sha256 = pseudoSha256,
                backupStatus = "PENDING_BACKUP",
            )
            val localMeta = item.uri?.let {
                LocalMediaMetadataEntity(
                    mediaId = mediaId,
                    localUri = it.toString(),
                    devicePath = item.absolutePath,
                    dateAdded = item.dateAdded * 1_000L,
                )
            }
            unifiedMediaDao.upsertUnifiedMedia(mediaEntity, localMeta)

            val task = BackupTaskEntity(
                taskId = "task_$mediaId",
                mediaId = mediaId,
                sessionId = activeSession.sessionId,
                priority = priority,
                state = "QUEUED",
                totalBytes = item.sizeBytes,
                createdAt = now,
                updatedAt = now,
            )
            newTasks.add(task)

            if (item.dateAdded > latestWatermark) {
                latestWatermark = item.dateAdded
            }
        }

        if (newTasks.isNotEmpty()) {
            backupTaskDao.insertTasks(newTasks)
            backupPreferences.setLastSeenTimestamp(latestWatermark)
            Log.i(TAG, "Queued ${newTasks.size} new continuous media tasks with P1 priority.")
            scheduler.executePendingTasks()
        }

        newTasks.size
    }
}
