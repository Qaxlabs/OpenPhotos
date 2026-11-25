package com.qaxlabs.openphotos.data

import com.qaxlabs.openphotos.data.db.AlbumDao
import com.qaxlabs.openphotos.data.db.AlbumEntity
import com.qaxlabs.openphotos.data.db.ItemAlbumCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepository @Inject constructor(
    private val albumDao: AlbumDao,
    private val indexRepository: IndexRepository,
) {
    /**
     * Observes all user albums sorted alphabetically.
     */
    val allAlbums: Flow<List<AlbumEntity>> = albumDao.observeAllAlbums()

    /**
     * Creates a new user album and pushes the updated index.
     */
    suspend fun createAlbum(title: String): AlbumEntity = withContext(Dispatchers.IO) {
        val albumId = UUID.randomUUID().toString()
        val album = AlbumEntity(
            albumId = albumId,
            title = title.trim(),
            createdAt = System.currentTimeMillis(),
            coverMediaId = null,
        )
        albumDao.insertAlbum(album)
        indexRepository.pushIndex()
        album
    }

    /**
     * Renames an existing user album.
     */
    suspend fun renameAlbum(albumId: String, newTitle: String) = withContext(Dispatchers.IO) {
        albumDao.renameAlbum(albumId, newTitle.trim())
        indexRepository.pushIndex()
    }

    /**
     * Deletes a user album without deleting the original media files.
     */
    suspend fun deleteAlbum(albumId: String) = withContext(Dispatchers.IO) {
        albumDao.deleteAlbum(albumId)
        indexRepository.pushIndex()
    }

    /**
     * Associates one or more media items with an album.
     */
    suspend fun addMediaToAlbum(albumId: String, mediaIds: List<String>) = withContext(Dispatchers.IO) {
        val refs = mediaIds.map { mediaId ->
            ItemAlbumCrossRef(albumId = albumId, mediaId = mediaId)
        }
        albumDao.insertCrossRefs(refs)

        // Set cover image if missing
        val album = albumDao.getAlbumById(albumId)
        if (album != null && album.coverMediaId.isNullOrEmpty() && mediaIds.isNotEmpty()) {
            albumDao.insertAlbum(album.copy(coverMediaId = mediaIds.first()))
        }

        indexRepository.pushIndex()
    }

    /**
     * Removes a media item from an album.
     */
    suspend fun removeMediaFromAlbum(albumId: String, mediaId: String) = withContext(Dispatchers.IO) {
        albumDao.removeCrossRef(albumId, mediaId)
        indexRepository.pushIndex()
    }

    /**
     * Observes media items belonging to a specific album.
     */
    fun observeMediaForAlbum(albumId: String): Flow<List<UnifiedMediaItem>> {
        return albumDao.observeMediaForAlbum(albumId).map { relations ->
            relations.map { it.toDomain() }
        }
    }
}
