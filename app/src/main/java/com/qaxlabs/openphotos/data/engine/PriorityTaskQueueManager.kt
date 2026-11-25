package com.qaxlabs.openphotos.data.engine

import com.qaxlabs.openphotos.data.MediaItem
import com.qaxlabs.openphotos.data.db.BackupTaskEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Priority levels for adaptive backup scheduling. */
object BackupPriority {
    const val P0_USER_SELECTED = 0 // Immediate manual uploads
    const val P1_NEW_MEDIA = 1      // Camera roll items created in last 24h
    const val P2_RECENT_MEDIA = 2   // Media created between 24h and 30 days ago
    const val P3_OLDER_MEDIA = 3    // Media created > 30 days ago (< 500 MB)
    const val P4_LARGE_MEDIA = 4    // Large videos (>= 500 MB) or deferred
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
private const val THIRTY_DAYS_MILLIS = 30 * DAY_MILLIS
private const val LARGE_FILE_BYTES = 500L * 1_024 * 1_024 // 500 MB

@Singleton
class PriorityTaskQueueManager @Inject constructor() {

    /**
     * Determines priority for a media item based on creation age, size, and user selection.
     */
    fun calculatePriority(
        media: MediaItem,
        isUserSelected: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): Int {
        if (isUserSelected) return BackupPriority.P0_USER_SELECTED
        if (media.sizeBytes >= LARGE_FILE_BYTES) return BackupPriority.P4_LARGE_MEDIA

        val ageMillis = now - (media.dateAdded * 1_000L)
        return when {
            ageMillis <= DAY_MILLIS -> BackupPriority.P1_NEW_MEDIA
            ageMillis <= THIRTY_DAYS_MILLIS -> BackupPriority.P2_RECENT_MEDIA
            else -> BackupPriority.P3_OLDER_MEDIA
        }
    }

    /**
     * Applies anti-starvation age weighting to prevent older tasks from waiting indefinitely.
     * Decrements priority level for tasks queued for longer than 24 hours.
     */
    fun applyAntiStarvation(
        tasks: List<BackupTaskEntity>,
        now: Long = System.currentTimeMillis(),
    ): List<BackupTaskEntity> {
        return tasks.map { task ->
            val queuedAgeMillis = now - task.createdAt
            if (queuedAgeMillis > DAY_MILLIS && task.priority > BackupPriority.P1_NEW_MEDIA) {
                val boostedPriority = (task.priority - 1).coerceAtLeast(BackupPriority.P1_NEW_MEDIA)
                task.copy(priority = boostedPriority)
            } else {
                task
            }
        }
    }
}
