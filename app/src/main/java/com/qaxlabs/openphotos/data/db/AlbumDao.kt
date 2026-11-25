package com.qaxlabs.openphotos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for managing user albums and media-to-album cross references.
 */
@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun observeAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY title ASC")
    suspend fun getAllAlbums(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE albumId = :albumId LIMIT 1")
    suspend fun getAlbumById(albumId: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity)

    @Query("UPDATE albums SET title = :newTitle WHERE albumId = :albumId")
    suspend fun renameAlbum(albumId: String, newTitle: String)

    @Query("DELETE FROM albums WHERE albumId = :albumId")
    suspend fun deleteAlbum(albumId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<ItemAlbumCrossRef>)

    @Query("DELETE FROM album_media_cross_ref WHERE albumId = :albumId AND mediaId = :mediaId")
    suspend fun removeCrossRef(albumId: String, mediaId: String)

    @Query("DELETE FROM album_media_cross_ref WHERE albumId = :albumId")
    suspend fun clearAlbumMedia(albumId: String)

    @Transaction
    @Query("SELECT * FROM media_items WHERE id IN (SELECT mediaId FROM album_media_cross_ref WHERE albumId = :albumId) AND trashState = 0 ORDER BY createdAt DESC")
    fun observeMediaForAlbum(albumId: String): Flow<List<UnifiedMediaRelation>>

    @Transaction
    @Query("SELECT * FROM media_items WHERE id IN (SELECT mediaId FROM album_media_cross_ref WHERE albumId = :albumId) AND trashState = 0 ORDER BY createdAt DESC")
    suspend fun getMediaForAlbum(albumId: String): List<UnifiedMediaRelation>

    @Query("SELECT COUNT(*) FROM album_media_cross_ref WHERE albumId = :albumId")
    fun observeItemCountForAlbum(albumId: String): Flow<Int>
}
