package com.qaxlabs.openphotos.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ScanProgress] state hierarchy.
 */
class MediaDiscoveryScannerTest {

    @Test
    fun scanProgressState_scanningFormat() {
        val progress = ScanProgress.Scanning(scannedCount = 150, totalEstimate = 1200)
        assertEquals(150, progress.scannedCount)
        assertEquals(1200, progress.totalEstimate)
    }

    @Test
    fun scanProgressState_completedFormat() {
        val progress = ScanProgress.Completed(totalDiscovered = 1200)
        assertEquals(1200, progress.totalDiscovered)
    }

    @Test
    fun scanProgressState_errorFormat() {
        val progress = ScanProgress.Error(message = "Permission denied")
        assertEquals("Permission denied", progress.message)
    }
}
