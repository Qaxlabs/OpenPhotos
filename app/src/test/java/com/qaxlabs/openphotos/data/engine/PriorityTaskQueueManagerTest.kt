package com.qaxlabs.openphotos.data.engine

import com.qaxlabs.openphotos.data.MediaItem
import com.qaxlabs.openphotos.data.db.BackupTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for priority assignment rules and anti-starvation age weighting.
 */
class PriorityTaskQueueManagerTest {

    private val manager = PriorityTaskQueueManager()

    @Test
    fun userSelectedMedia_assignedP0Priority() {
        val media = createMediaItem(ageHours = 2)
        val priority = manager.calculatePriority(media, isUserSelected = true)
        assertEquals(BackupPriority.P0_USER_SELECTED, priority)
    }

    @Test
    fun newMediaWithin24Hours_assignedP1Priority() {
        val media = createMediaItem(ageHours = 2)
        val priority = manager.calculatePriority(media, isUserSelected = false)
        assertEquals(BackupPriority.P1_NEW_MEDIA, priority)
    }

    @Test
    fun recentMedia10DaysOld_assignedP2Priority() {
        val media = createMediaItem(ageHours = 10 * 24)
        val priority = manager.calculatePriority(media, isUserSelected = false)
        assertEquals(BackupPriority.P2_RECENT_MEDIA, priority)
    }

    @Test
    fun olderMedia40DaysOld_assignedP3Priority() {
        val media = createMediaItem(ageHours = 40 * 24)
        val priority = manager.calculatePriority(media, isUserSelected = false)
        assertEquals(BackupPriority.P3_OLDER_MEDIA, priority)
    }

    @Test
    fun largeMedia_assignedP4Priority() {
        val media = createMediaItem(ageHours = 2, sizeMb = 600)
        val priority = manager.calculatePriority(media, isUserSelected = false)
        assertEquals(BackupPriority.P4_LARGE_MEDIA, priority)
    }

    @Test
    fun antiStarvation_boostsPriorityForTasksQueuedOver24Hours() {
        val now = System.currentTimeMillis()
        val dayAndHalfAgo = now - (36 * 60 * 60 * 1000L)

        val task = BackupTaskEntity(
            taskId = "task_1",
            mediaId = "media_1",
            sessionId = "session_1",
            priority = BackupPriority.P3_OLDER_MEDIA,
            state = "QUEUED",
            createdAt = dayAndHalfAgo,
            updatedAt = dayAndHalfAgo,
        )

        val boostedList = manager.applyAntiStarvation(listOf(task), now = now)
        assertEquals(BackupPriority.P2_RECENT_MEDIA, boostedList.first().priority)
    }

    private fun createMediaItem(ageHours: Int, sizeMb: Long = 5): MediaItem {
        val nowSecs = System.currentTimeMillis() / 1000L
        val dateAdded = nowSecs - (ageHours * 3600L)
        return MediaItem(
            id = 101L,
            uri = null,
            displayName = "sample.jpg",
            sizeBytes = sizeMb * 1024 * 1024L,
            mimeType = "image/jpeg",
            dateAdded = dateAdded,
            absolutePath = null,
        )
    }
}
