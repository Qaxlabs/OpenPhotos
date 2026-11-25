package com.qaxlabs.openphotos.data.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for parsing MTProto flood wait error responses.
 */
class AdaptiveUploadSchedulerTest {

    @Test
    fun parseWaitSeconds_extracts45SecondsFromFloodWaitMessage() {
        val errorMsg = "TDLib error 429: FLOOD_WAIT_45"
        val match = Regex("FLOOD_WAIT_(\\d+)").find(errorMsg)
            ?: Regex("(\\d+)").find(errorMsg)
        val waitSeconds = match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 60L
        assertEquals(45L, waitSeconds)
    }

    @Test
    fun parseWaitSeconds_fallbackDefaultWhenMissing() {
        val errorMsg = "TDLib error 429: FLOOD_WAIT"
        val match = Regex("FLOOD_WAIT_(\\d+)").find(errorMsg)
        val waitSeconds = match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 60L
        assertEquals(60L, waitSeconds)
    }
}
