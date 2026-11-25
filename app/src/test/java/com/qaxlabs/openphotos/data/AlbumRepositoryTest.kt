package com.qaxlabs.openphotos.data

import com.qaxlabs.openphotos.data.db.AlbumEntity
import com.qaxlabs.openphotos.data.db.ItemAlbumCrossRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for album data entities and cross-references.
 */
class AlbumRepositoryTest {

    @Test
    fun albumEntity_propertiesMatch() {
        val album = AlbumEntity(
            albumId = "album_101",
            title = "Vacation 2026",
            createdAt = 1700000000000L,
            coverMediaId = "media_101",
        )

        assertEquals("album_101", album.albumId)
        assertEquals("Vacation 2026", album.title)
        assertEquals("media_101", album.coverMediaId)
    }

    @Test
    fun itemAlbumCrossRef_associationsMatch() {
        val ref = ItemAlbumCrossRef(
            albumId = "album_101",
            mediaId = "media_505",
        )

        assertNotNull(ref)
        assertEquals("album_101", ref.albumId)
        assertEquals("media_505", ref.mediaId)
    }
}
