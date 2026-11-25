package com.qaxlabs.openphotos.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing user albums for media curation.
 */
@Entity(
    tableName = "albums",
    indices = [Index(value = ["title"])],
)
data class AlbumEntity(
    @PrimaryKey
    val albumId: String,
    val title: String,
    val createdAt: Long,
    val coverMediaId: String? = null,
)

/**
 * Junction table mapping media items to user albums.
 */
@Entity(
    tableName = "album_media_cross_ref",
    primaryKeys = ["albumId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["albumId"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["mediaId"]),
    ],
)
data class ItemAlbumCrossRef(
    val albumId: String,
    val mediaId: String,
)
