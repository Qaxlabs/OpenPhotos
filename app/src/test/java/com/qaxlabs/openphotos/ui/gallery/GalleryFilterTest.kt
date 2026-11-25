package com.qaxlabs.openphotos.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GalleryFilter] enum entries.
 */
class GalleryFilterTest {

    @Test
    fun galleryFilterEntries_containAllOptions() {
        val entries = GalleryFilter.entries.map { it.name }
        assertTrue(entries.contains("ALL"))
        assertTrue(entries.contains("PHOTOS"))
        assertTrue(entries.contains("VIDEOS"))
        assertTrue(entries.contains("LOCAL_ONLY"))
        assertTrue(entries.contains("CLOUD_ONLY"))
        assertTrue(entries.contains("BACKED_UP"))
        assertTrue(entries.contains("PENDING_BACKUP"))
        assertEquals(7, entries.size)
    }
}
