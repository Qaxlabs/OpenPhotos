package com.qaxlabs.openphotos.data

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.qaxlabs.openphotos.data.util.MediaDiscoveryScanner
import com.qaxlabs.openphotos.data.util.MediaStoreContentObserver
import com.qaxlabs.openphotos.data.util.ScanProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries the device's [MediaStore] for photos and videos, newest first.
 * Integrates [MediaDiscoveryScanner] and [MediaStoreContentObserver] for automated discovery.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: MediaDiscoveryScanner,
    private val observer: MediaStoreContentObserver,
) {
    companion object {
        private const val SORT_ORDER = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        @Suppress("DEPRECATION")
        private val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATA,
        )
    }

    val scanProgress: StateFlow<ScanProgress> = scanner.progress

    /** Checks if required media permissions are granted based on API level. */
    fun hasMediaPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val img = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val vid = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            img && vid
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Triggers a full media discovery scan across device storage. */
    suspend fun startFullScan(forceShowProgress: Boolean = false): Int {
        if (!hasMediaPermissions()) return 0
        return scanner.performFullScan(forceShowProgress)
    }

    /** Registers ContentObserver for real-time camera roll updates. */
    fun registerObserver(scope: CoroutineScope, onChange: () -> Unit) {
        if (!hasMediaPermissions()) return
        observer.register(scope, onChange)
    }

    /** Unregisters ContentObserver. */
    fun unregisterObserver() {
        observer.unregister()
    }

    /**
     * Returns all images and videos from external storage, merged and sorted newest-first.
     */
    suspend fun queryAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        buildList {
            addAll(queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
            addAll(queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
            sortByDescending { it.dateAdded }
        }
    }

    @Suppress("DEPRECATION")
    private fun queryCollection(collectionUri: Uri): List<MediaItem> {
        val results = mutableListOf<MediaItem>()

        context.contentResolver.query(
            collectionUri,
            PROJECTION,
            null,
            null,
            SORT_ORDER,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: "Unknown"
                val sizeBytes = cursor.getLong(sizeCol)
                val mimeType = cursor.getString(mimeCol) ?: "application/octet-stream"
                val dateAdded = cursor.getLong(dateCol)
                val absolutePath = if (dataCol >= 0) cursor.getString(dataCol) else null

                val contentUri = ContentUris.withAppendedId(collectionUri, id)

                results += MediaItem(
                    id = id,
                    uri = contentUri,
                    displayName = displayName,
                    sizeBytes = sizeBytes,
                    mimeType = mimeType,
                    dateAdded = dateAdded,
                    absolutePath = absolutePath,
                )
            }
        }

        return results
    }
}
