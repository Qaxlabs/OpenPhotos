package com.qaxlabs.openphotos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for adaptive backup tasks and persistent backup sessions.
 */
@Dao
interface BackupTaskDao {

    @Query("SELECT * FROM backup_sessions WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<BackupSessionEntity?>

    @Query("SELECT * FROM backup_sessions WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveSession(): BackupSessionEntity?

    @Query("SELECT * FROM backup_sessions WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY createdAt DESC")
    suspend fun getActiveOrPausedSessions(): List<BackupSessionEntity>

    @Query("SELECT * FROM backup_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): BackupSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BackupSessionEntity)

    @Query("UPDATE backup_sessions SET status = :status WHERE sessionId = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: String)

    @Query("UPDATE backup_sessions SET totalItems = :totalItems, totalBytes = :totalBytes WHERE sessionId = :sessionId")
    suspend fun updateSessionTotals(sessionId: String, totalItems: Int, totalBytes: Long)

    @Query("UPDATE backup_sessions SET completedItems = :completedItems, failedItems = :failedItems, uploadedBytes = :uploadedBytes WHERE sessionId = :sessionId")
    suspend fun updateSessionProgress(sessionId: String, completedItems: Int, failedItems: Int, uploadedBytes: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: BackupTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<BackupTaskEntity>)

    @Query("SELECT * FROM backup_tasks WHERE state IN ('QUEUED', 'FLOOD_WAIT') AND nextAttemptTimestamp <= :currentTime ORDER BY priority ASC, createdAt ASC LIMIT :limit")
    suspend fun getPendingTasks(currentTime: Long, limit: Int = 20): List<BackupTaskEntity>

    @Query("SELECT * FROM backup_tasks WHERE sessionId = :sessionId ORDER BY priority ASC, createdAt ASC")
    fun observeTasksForSession(sessionId: String): Flow<List<BackupTaskEntity>>

    @Query("SELECT * FROM backup_tasks WHERE sessionId = :sessionId ORDER BY priority ASC, createdAt ASC")
    suspend fun getTasksForSession(sessionId: String): List<BackupTaskEntity>

    @Query("SELECT * FROM backup_tasks WHERE mediaId IN (:mediaIds)")
    suspend fun getTasksForMediaIds(mediaIds: List<String>): List<BackupTaskEntity>

    @Query("UPDATE backup_tasks SET sessionId = :targetSessionId WHERE sessionId = :sourceSessionId")
    suspend fun reassignSessionTasks(sourceSessionId: String, targetSessionId: String)

    @Query("SELECT * FROM backup_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): BackupTaskEntity?

    @Query("SELECT * FROM backup_tasks WHERE mediaId = :mediaId AND state = 'COMPLETED' LIMIT 1")
    suspend fun getCompletedTaskForMedia(mediaId: String): BackupTaskEntity?

    @Query("UPDATE backup_tasks SET state = :state, lastError = :lastError, nextAttemptTimestamp = :nextAttemptTimestamp, retryCount = :retryCount, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun updateTaskState(
        taskId: String,
        state: String,
        lastError: String? = null,
        nextAttemptTimestamp: Long = 0L,
        retryCount: Int = 0,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE backup_tasks SET bytesUploaded = :bytesUploaded, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun updateTaskProgress(
        taskId: String,
        bytesUploaded: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE backup_tasks SET priority = :newPriority, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun updateTaskPriority(
        taskId: String,
        newPriority: Int,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM backup_tasks WHERE sessionId = :sessionId")
    suspend fun deleteTasksForSession(sessionId: String)

    @Query("DELETE FROM backup_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    /**
     * Atomically reconciles sessions and appends deduplicated user backup tasks.
     */
    @Transaction
    suspend fun createOrAppendUserSessionTransaction(
        candidateMediaList: List<CandidateMedia>,
        priority: Int,
        now: Long = System.currentTimeMillis(),
    ): BackupSessionEntity {
        // 1. Reconcile active/paused sessions (ensure exactly one primary session)
        val activeOrPaused = getActiveOrPausedSessions()
        val primarySession = if (activeOrPaused.isNotEmpty()) {
            val primary = activeOrPaused.first()
            for (i in 1 until activeOrPaused.size) {
                val secondary = activeOrPaused[i]
                reassignSessionTasks(secondary.sessionId, primary.sessionId)
                updateSessionStatus(secondary.sessionId, "COMPLETED")
            }
            primary
        } else {
            val newSession = BackupSessionEntity(
                sessionId = UUID.randomUUID().toString(),
                sessionType = "USER_SELECTED",
                createdAt = now,
                status = "ACTIVE",
                totalItems = 0,
                totalBytes = 0L,
            )
            insertSession(newSession)
            newSession
        }

        // 2. Query existing tasks for deduplication & failure reset
        val candidateMediaIds = candidateMediaList.map { it.mediaId }
        val existingTasks = getTasksForMediaIds(candidateMediaIds).associateBy { it.mediaId }

        val tasksToInsert = mutableListOf<BackupTaskEntity>()

        for (candidate in candidateMediaList) {
            val existing = existingTasks[candidate.mediaId]
            if (existing != null) {
                when (existing.state) {
                    "COMPLETED", "UPLOADING", "QUEUED", "FLOOD_WAIT", "PAUSED", "ACCOUNT_RESTRICTED" -> {
                        // Skip candidate item — already active or backed up
                        continue
                    }
                    "FAILED", "CANCELLED" -> {
                        // Reset failed/cancelled task
                        updateTaskState(
                            taskId = existing.taskId,
                            state = "QUEUED",
                            lastError = null,
                            nextAttemptTimestamp = 0L,
                            retryCount = 0,
                            updatedAt = now,
                        )
                        updateTaskPriority(existing.taskId, priority, now)
                    }
                }
            } else {
                // New task
                tasksToInsert.add(
                    BackupTaskEntity(
                        taskId = UUID.randomUUID().toString(),
                        mediaId = candidate.mediaId,
                        sessionId = primarySession.sessionId,
                        priority = priority,
                        state = "QUEUED",
                        totalBytes = candidate.sizeBytes,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }

        if (tasksToInsert.isNotEmpty()) {
            insertTasks(tasksToInsert)
        }

        // 3. Recalculate session totals
        val allSessionTasks = getTasksForSession(primarySession.sessionId)
        val updatedTotalItems = allSessionTasks.size
        val updatedTotalBytes = allSessionTasks.sumOf { it.totalBytes }
        updateSessionTotals(primarySession.sessionId, updatedTotalItems, updatedTotalBytes)

        return getSession(primarySession.sessionId) ?: primarySession
    }

    data class CandidateMedia(
        val mediaId: String,
        val sizeBytes: Long,
    )
}
