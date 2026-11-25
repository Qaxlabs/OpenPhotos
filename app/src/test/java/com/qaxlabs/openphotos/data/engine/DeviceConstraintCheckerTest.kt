package com.qaxlabs.openphotos.data.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying device constraint data types.
 */
class DeviceConstraintCheckerTest {

    @Test
    fun priorityConstants_orderedCorrectly() {
        assertTrue(BackupPriority.P0_USER_SELECTED < BackupPriority.P1_NEW_MEDIA)
        assertTrue(BackupPriority.P1_NEW_MEDIA < BackupPriority.P2_RECENT_MEDIA)
        assertTrue(BackupPriority.P2_RECENT_MEDIA < BackupPriority.P3_OLDER_MEDIA)
        assertTrue(BackupPriority.P3_OLDER_MEDIA < BackupPriority.P4_LARGE_MEDIA)
    }
}
