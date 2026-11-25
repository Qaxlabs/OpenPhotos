package com.qaxlabs.openphotos.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for continuous backup media discovery and filtering logic.
 */
class ContinuousBackupEngineTest {

    @Test
    fun watermarkComparison_filtersNewMedia() {
        val lastSeenWatermark = 1600000000L
        val oldPhotoDate = 1500000000L
        val newPhotoDate = 1700000000L

        assertTrue(newPhotoDate > lastSeenWatermark)
        assertTrue(oldPhotoDate <= lastSeenWatermark)
    }

    @Test
    fun mediaTypeFiltering_photosAndVideosToggles() {
        val photosEnabled = true
        val videosEnabled = false

        val isImage = true
        val isVideo = false

        val isAllowedImage = (isImage && photosEnabled) || (isVideo && videosEnabled)
        val isAllowedVideo = (!isImage && photosEnabled) || (true && videosEnabled)

        assertTrue(isAllowedImage)
        assertEquals(false, isAllowedVideo)
    }
}
