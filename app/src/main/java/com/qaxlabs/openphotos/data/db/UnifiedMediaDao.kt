package com.qaxlabs.openphotos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for accessing and mutating the unified media database.
 */
@Dao
interface UnifiedMediaDao {

    @Transaction
    @Query("SELECT * FROM media_items WHERE trashState = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getUnifiedMediaPaged(limit: Int, offset: Int): List<UnifiedMediaRelation>

    @Transaction
    @Query("SELECT * FROM media_items WHERE trashState = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun observeUnifiedMediaPaged(limit: Int, offset: Int): Flow<List<UnifiedMediaRelation>>

    @Transaction
    @Query("SELECT * FROM media_items WHERE trashState = 0 ORDER BY createdAt DESC")
    fun observeAllUnifiedMedia(): Flow<List<UnifiedMediaRelation>>

    @Transaction
    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UnifiedMediaRelation?

    @Query("SELECT * FROM media_items WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): MediaEntity?

    @Transaction
    @Query("SELECT * FROM media_items WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findRelationBySha256(sha256: String): UnifiedMediaRelation?

    @Query("SELECT COUNT(*) FROM media_items WHERE trashState = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE trashState = 0")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalMetadata(metadata: LocalMediaMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemoteMetadata(metadata: RemoteMediaMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupTask(task: BackupTaskEntity)

    @Transaction
    suspend fun upsertUnifiedMedia(
        media: MediaEntity,
        localMetadata: LocalMediaMetadataEntity? = null,
        remoteMetadata: RemoteMediaMetadataEntity? = null,
        backupTask: BackupTaskEntity? = null,
    ) {
        insertMedia(media)
        localMetadata?.let { insertLocalMetadata(it) }
        remoteMetadata?.let { insertRemoteMetadata(it) }
        backupTask?.let { insertBackupTask(it) }
    }

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM media_items")
    suspend fun clearAll()
}
