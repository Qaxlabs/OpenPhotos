package com.qaxlabs.openphotos.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [StorageStats] calculations and metadata formatting.
 */
class StorageManagementRepositoryTest {

    @Test
    fun storageStats_defaultValuesAreZero() {
        val stats = StorageStats()
        assertEquals(0, stats.localCount)
        assertEquals(0, stats.cloudCount)
        assertEquals(0L, stats.totalLocalBytes)
        assertEquals(0L, stats.recoverableBytes)
    }

    @Test
    fun storageStats_customValuesMatch() {
        val stats = StorageStats(
            localCount = 150,
            cloudCount = 120,
            backedUpCount = 100,
            totalLocalBytes = 500_000_000L,
            recoverableBytes = 300_000_000L,
        )

        assertEquals(150, stats.localCount)
        assertEquals(120, stats.cloudCount)
        assertEquals(100, stats.backedUpCount)
        assertEquals(500_000_000L, stats.totalLocalBytes)
        assertEquals(300_000_000L, stats.recoverableBytes)
    }
}
