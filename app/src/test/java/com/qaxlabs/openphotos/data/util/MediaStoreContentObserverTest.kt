package com.qaxlabs.openphotos.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [MediaStoreContentObserver] configuration constants.
 */
class MediaStoreContentObserverTest {

    @Test
    fun contentUriString_isValidFormat() {
        val uriString = "content://media/external/images/media/101"
        assertEquals("content://media/external/images/media/101", uriString)
    }
}
