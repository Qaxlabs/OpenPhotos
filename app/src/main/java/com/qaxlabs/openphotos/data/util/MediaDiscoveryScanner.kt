package com.qaxlabs.openphotos.data.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.qaxlabs.openphotos.data.BackupStatus
import com.qaxlabs.openphotos.data.db.LocalMediaMetadataEntity
import com.qaxlabs.openphotos.data.db.MediaEntity
import com.qaxlabs.openphotos.data.db.UnifiedMediaDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State of device media library scanning.
 */
sealed interface ScanProgress {
    object Idle : ScanProgress
    data class Scanning(val scannedCount: Int, val totalEstimate: Int) : ScanProgress
    data class Completed(val totalDiscovered: Int) : ScanProgress
    data class Error(val message: String) : ScanProgress
}

/**
 * High-performance MediaStore scanner that discovers device photos and videos.
 *
 * Lightweight initial scanning: Reads file properties, dimensions, durations,
 * and timestamps without computing expensive full-file SHA-256 hashes upfront.
 */
@Singleton
class MediaDiscoveryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: UnifiedMediaDao,
) {
    companion object {
        private const val TAG = "MediaDiscoveryScanner"
        private const val BATCH_SIZE = 200
        private const val SORT_ORDER = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        @Suppress("DEPRECATION")
        private val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATA,
        )

        private val VIDEO_PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
        )
    }

    private val _progress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    /**
     * Executes a full scan of local device images and videos on [Dispatchers.IO].
     * Reads lightweight metadata and inserts into Room in batch chunks of [BATCH_SIZE].
     */
    suspend fun performFullScan(forceShowProgress: Boolean = false): Int = withContext(Dispatchers.IO) {
        val currentDbCount = dao.getCount()
        val showProgress = forceShowProgress || currentDbCount == 0

        if (showProgress) {
            _progress.value = ScanProgress.Scanning(0, 0)
        }
        var totalDiscovered = 0

        try {
            val imageList = scanCollection(
                collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                isVideo = false,
            )
            val videoList = scanCollection(
                collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                isVideo = true,
            )

            val merged = (imageList + videoList).sortedByDescending { it.media.createdAt }
            val totalEstimate = merged.size
            if (showProgress) {
                _progress.value = ScanProgress.Scanning(0, totalEstimate)
            }

            // Batch insert into database in chunks of BATCH_SIZE
            merged.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { item ->
                    // Preserve existing record backupStatus if present
                    val existing = dao.findBySha256(item.media.sha256)
                    val status = existing?.backupStatus ?: BackupStatus.LOCAL_ONLY.name
                    val updatedMedia = item.media.copy(backupStatus = status)

                    dao.upsertUnifiedMedia(
                        media = updatedMedia,
                        localMetadata = item.localMeta,
                    )
                }
                totalDiscovered += batch.size
                if (showProgress) {
                    _progress.value = ScanProgress.Scanning(totalDiscovered, totalEstimate)
                }
            }

            Log.i(TAG, "Completed full media discovery scan. Discovered $totalDiscovered files.")
            _progress.value = ScanProgress.Completed(totalDiscovered)
            totalDiscovered
        } catch (e: Exception) {
            Log.e(TAG, "Error during media discovery scan: ${e.message}", e)
            _progress.value = ScanProgress.Error(e.message ?: "Failed to scan media")
            0
        }
    }

    private data class DiscoveredItem(
        val media: MediaEntity,
        val localMeta: LocalMediaMetadataEntity,
    )

    @Suppress("DEPRECATION")
    private fun scanCollection(collectionUri: Uri, isVideo: Boolean): List<DiscoveredItem> {
        val results = mutableListOf<DiscoveredItem>()
        val projection = if (isVideo) VIDEO_PROJECTION else PROJECTION

        context.contentResolver.query(
            collectionUri,
            projection,
            null,
            null,
            SORT_ORDER,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val durationCol = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
            val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: "Media_$id"
                val sizeBytes = cursor.getLong(sizeCol)
                val mimeType = cursor.getString(mimeCol) ?: if (isVideo) "video/mp4" else "image/jpeg"
                val dateAddedSeconds = cursor.getLong(dateAddedCol)
                val dateModSeconds = cursor.getLong(dateModCol)
                val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                val absolutePath = if (dataCol >= 0) cursor.getString(dataCol) else null

                val contentUri = ContentUris.withAppendedId(collectionUri, id)
                val createdAtMillis = if (dateAddedSeconds > 0) dateAddedSeconds * 1_000L else System.currentTimeMillis()
                val modifiedAtMillis = if (dateModSeconds > 0) dateModSeconds * 1_000L else createdAtMillis

                // Stable media ID and lightweight pseudo-checksum for fast initial indexing
                val mediaId = "local_$id"
                val pseudoSha256 = "sha256:local_${id}_${sizeBytes}_$dateAddedSeconds"

                val mediaEntity = MediaEntity(
                    id = mediaId,
                    filename = displayName,
                    mimeType = mimeType,
                    mediaType = if (isVideo) "VIDEO" else "IMAGE",
                    sizeBytes = sizeBytes,
                    width = width,
                    height = height,
                    durationMs = durationMs,
                    createdAt = createdAtMillis,
                    modifiedAt = modifiedAtMillis,
                    sha256 = pseudoSha256,
                    backupStatus = BackupStatus.LOCAL_ONLY.name,
                )

                val localMeta = LocalMediaMetadataEntity(
                    mediaId = mediaId,
                    localUri = contentUri.toString(),
                    devicePath = absolutePath,
                    dateAdded = createdAtMillis,
                )

                results += DiscoveredItem(mediaEntity, localMeta)
            }
        }

        return results
    }
}
