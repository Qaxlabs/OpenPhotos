package com.qaxlabs.openphotos.data.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time [ContentObserver] listening for MediaStore changes (new camera photos/videos).
 */
@Singleton
class MediaStoreContentObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "MediaStoreContentObserver"
        private const val DEBOUNCE_DELAY_MS = 1_200L
    }

    private var onChangeCallback: (() -> Unit)? = null
    private var isRegistered = false
    private var debounceJob: Job? = null

    /**
     * Registers observers on MediaStore Images and Video URIs.
     */
    fun register(scope: CoroutineScope, onChange: () -> Unit) {
        if (isRegistered) return
        this.onChangeCallback = onChange

        try {
            val resolver = context.contentResolver
            resolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                this,
            )
            resolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                this,
            )
            isRegistered = true
            Log.i(TAG, "Registered MediaStoreContentObserver successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register MediaStoreContentObserver: ${e.message}")
        }
    }

    /**
     * Unregisters the content observer.
     */
    fun unregister() {
        if (!isRegistered) return
        try {
            context.contentResolver.unregisterContentObserver(this)
            isRegistered = false
            onChangeCallback = null
            Log.i(TAG, "Unregistered MediaStoreContentObserver.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister MediaStoreContentObserver: ${e.message}")
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "MediaStore change detected on URI: $uri")

        // Debounce rapid camera writes
        debounceJob?.cancel()
        debounceJob = CoroutineScope(Dispatchers.Default).launch {
            delay(DEBOUNCE_DELAY_MS)
            onChangeCallback?.invoke()
        }
    }
}
