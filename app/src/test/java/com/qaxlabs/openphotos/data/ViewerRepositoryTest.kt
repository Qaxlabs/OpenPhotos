package com.qaxlabs.openphotos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ViewerFileState] hierarchy and media detail viewer states.
 */
class ViewerRepositoryTest {

    @Test
    fun viewerFileState_loadingProgress() {
        val state = ViewerFileState.Loading(progress = 0.75f)
        assertEquals(0.75f, state.progress)
    }

    @Test
    fun viewerFileState_readyPath() {
        val path = "/storage/emulated/0/Pictures/OpenPhotos/IMG_1001.jpg"
        val state = ViewerFileState.Ready(localPath = path)
        assertEquals(path, state.localPath)
    }

    @Test
    fun viewerFileState_errorMessage() {
        val errorMsg = "Download timed out"
        val state = ViewerFileState.Error(message = errorMsg)
        assertEquals(errorMsg, state.message)
        assertTrue(state is ViewerFileState.Error)
    }
}
