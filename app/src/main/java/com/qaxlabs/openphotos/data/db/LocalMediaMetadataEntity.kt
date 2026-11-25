package com.qaxlabs.openphotos.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Room entity storing device-specific MediaStore properties for local media files.
 */
@Entity(
    tableName = "local_media_metadata",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LocalMediaMetadataEntity(
    @PrimaryKey
    val mediaId: String,
    val localUri: String,
    val devicePath: String?,
    val dateAdded: Long,
)
