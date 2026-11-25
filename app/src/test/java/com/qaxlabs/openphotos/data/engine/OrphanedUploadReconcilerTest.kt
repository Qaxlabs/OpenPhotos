package com.qaxlabs.openphotos.data.engine

import com.qaxlabs.openphotos.data.VaultIndexV2Spec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for checking filenames ignored by OrphanedUploadReconciler.
 */
class OrphanedUploadReconcilerTest {

    @Test
    fun isIndexOrJournalFilename_identifiesIndexFiles() {
        val v1Name = VaultIndexV2Spec.V1_INDEX_FILENAME
        val v2Name = VaultIndexV2Spec.V2_INDEX_FILENAME
        val journalName = "openphotos_journal_42.json"
        val userFileName = "IMG_20260330.jpg"

        assertTrue(v1Name == VaultIndexV2Spec.V1_INDEX_FILENAME)
        assertTrue(v2Name == VaultIndexV2Spec.V2_INDEX_FILENAME)
        assertTrue(journalName.startsWith(VaultIndexV2Spec.JOURNAL_PREFIX))
        assertFalse(userFileName.startsWith(VaultIndexV2Spec.JOURNAL_PREFIX))
    }
}
