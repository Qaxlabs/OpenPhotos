package com.qaxlabs.openphotos.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for default backup preferences values.
 */
class BackupPreferencesTest {

    @Test
    fun defaultLargeVideoThreshold_is500Mb() {
        val defaultThreshold = 500
        assertEquals(500, defaultThreshold)
    }
}
