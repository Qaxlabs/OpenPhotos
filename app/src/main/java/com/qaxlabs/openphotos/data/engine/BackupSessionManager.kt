package com.qaxlabs.openphotos.data.engine

import com.qaxlabs.openphotos.data.MediaItem
import com.qaxlabs.openphotos.data.WorkScheduler
import com.qaxlabs.openphotos.data.db.BackupSessionEntity
import com.qaxlabs.openphotos.data.db.BackupTaskDao
import com.qaxlabs.openphotos.data.db.BackupTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSessionManager @Inject constructor(
    private val backupTaskDao: BackupTaskDao,
    private val priorityManager: PriorityTaskQueueManager,
    private val workScheduler: WorkScheduler,
) {
    /**
     * Observes current active backup session.
     */
    fun observeActiveSession(): Flow<BackupSessionEntity?> = backupTaskDao.observeActiveSession()

    /**
     * Starts or appends user-selected items to an active backup session.
     * Transaction completes in Room BEFORE scheduling WorkManager.
     */
    suspend fun startUserBackupSession(mediaList: List<MediaItem>): BackupSessionEntity = withContext(Dispatchers.IO) {
        val candidates = mediaList.map { media ->
            BackupTaskDao.CandidateMedia(
                mediaId = "local_${media.id}",
                sizeBytes = media.sizeBytes,
            )
        }

        // 1. Execute Room transaction to create/append session and deduplicate tasks
        val session = backupTaskDao.createOrAppendUserSessionTransaction(
            candidateMediaList = candidates,
            priority = 0, // P0_USER_SELECTED
        )

        // 2. Schedule WorkManager ONLY AFTER transaction commits AND only if status is ACTIVE
        if (session.status == "ACTIVE") {
            runCatching { workScheduler.scheduleImmediate() }
        }

        session
    }

    /**
     * Creates and persists an initial backup session for unbacked-up device media.
     */
    suspend fun createInitialBackupSession(mediaList: List<MediaItem>): BackupSessionEntity = withContext(Dispatchers.IO) {
        val activeSession = backupTaskDao.getActiveSession()
        if (activeSession != null) return@withContext activeSession

        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val totalBytes = mediaList.sumOf { it.sizeBytes }

        val session = BackupSessionEntity(
            sessionId = sessionId,
            sessionType = "INITIAL",
            createdAt = now,
            status = "ACTIVE",
            totalItems = mediaList.size,
            totalBytes = totalBytes,
        )

        val tasks = mediaList.map { media ->
            val priority = priorityManager.calculatePriority(media, isUserSelected = false, now = now)
            BackupTaskEntity(
                taskId = UUID.randomUUID().toString(),
                mediaId = "local_${media.id}",
                sessionId = sessionId,
                priority = priority,
                state = "QUEUED",
                totalBytes = media.sizeBytes,
                createdAt = now,
                updatedAt = now,
            )
        }

        backupTaskDao.insertSession(session)
        backupTaskDao.insertTasks(tasks)
        runCatching { workScheduler.scheduleImmediate() }
        session
    }

    /**
     * Pauses the active backup session.
     */
    suspend fun pauseActiveSession() = withContext(Dispatchers.IO) {
        val activeSession = backupTaskDao.getActiveSession() ?: return@withContext
        backupTaskDao.updateSessionStatus(activeSession.sessionId, "PAUSED")
    }

    /**
     * Resumes the paused backup session.
     */
    suspend fun resumeActiveSession() = withContext(Dispatchers.IO) {
        val activeSession = backupTaskDao.getActiveSession()
        if (activeSession != null && activeSession.status == "PAUSED") {
            backupTaskDao.updateSessionStatus(activeSession.sessionId, "ACTIVE")
            runCatching { workScheduler.scheduleImmediate() }
        }
    }

    /**
     * Cancels the active backup session and removes queued tasks.
     */
    suspend fun cancelActiveSession() = withContext(Dispatchers.IO) {
        val activeSession = backupTaskDao.getActiveSession() ?: return@withContext
        backupTaskDao.updateSessionStatus(activeSession.sessionId, "CANCELLED")
        backupTaskDao.deleteTasksForSession(activeSession.sessionId)
    }
}
