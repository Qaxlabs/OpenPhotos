package com.qaxlabs.openphotos.ui.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qaxlabs.openphotos.data.AlbumRepository
import com.qaxlabs.openphotos.data.BackupStatus
import com.qaxlabs.openphotos.data.MediaItem
import com.qaxlabs.openphotos.data.MediaRepository
import com.qaxlabs.openphotos.data.MediaType
import com.qaxlabs.openphotos.data.ThumbnailRepository
import com.qaxlabs.openphotos.data.UnifiedMediaItem
import com.qaxlabs.openphotos.data.UnifiedMediaRepository
import com.qaxlabs.openphotos.data.UploadQueueItem
import com.qaxlabs.openphotos.data.UploadRepository
import com.qaxlabs.openphotos.data.UploadState
import com.qaxlabs.openphotos.data.ViewerFileState
import com.qaxlabs.openphotos.data.ViewerRepository
import com.qaxlabs.openphotos.data.db.AlbumEntity
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import com.qaxlabs.openphotos.data.db.UploadedItemEntity
import com.qaxlabs.openphotos.data.util.ScanProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val headerDateFormatter = DateTimeFormatter
    .ofPattern("EEE, MMM d, yyyy")
    .withZone(ZoneId.systemDefault())

fun getItemDate(epochMillis: Long): LocalDate {
    if (epochMillis <= 0L) return LocalDate.MIN
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}

fun formatDateHeader(date: LocalDate): String {
    val today = LocalDate.now(ZoneId.systemDefault())
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date == LocalDate.MIN -> "Unknown Date"
        else -> headerDateFormatter.format(date)
    }
}

data class DateMediaGroup(
    val dateKey: LocalDate,
    val headerTitle: String,
    val items: List<VaultGalleryItem>,
)

enum class GalleryFilter {
    ALL,
    PHOTOS,
    VIDEOS,
    LOCAL_ONLY,
    CLOUD_ONLY,
    BACKED_UP,
    PENDING_BACKUP,
}

data class VaultGalleryItem(
    val indexId: String,
    val localUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val isUploading: Boolean,
    val isBackedUp: Boolean,
    val backupStatus: BackupStatus = BackupStatus.LOCAL_ONLY,
    val entity: UploadedItemEntity? = null,
    val thumbnailPath: String? = null,
    val takenAt: Long = 0L,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Failure(val reason: String) : DownloadResult
}

/**
 * ViewModel for [GalleryScreen] and [GalleryDetailScreen].
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val dao: UploadedItemDao,
    private val uploadRepository: UploadRepository,
    private val thumbnailRepository: ThumbnailRepository,
    private val viewerRepository: ViewerRepository,
    private val unifiedMediaRepository: UnifiedMediaRepository,
    private val mediaRepository: MediaRepository,
    private val albumRepository: AlbumRepository,
) : ViewModel() {

    val scanProgress: StateFlow<ScanProgress> = mediaRepository.scanProgress

    val allAlbums: StateFlow<List<AlbumEntity>> = albumRepository.allAlbums
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _unifiedItems = MutableStateFlow<List<UnifiedMediaItem>>(emptyList())
    val unifiedItems: StateFlow<List<UnifiedMediaItem>> = _unifiedItems.asStateFlow()

    private val _activeFilter = MutableStateFlow(GalleryFilter.ALL)
    val activeFilter: StateFlow<GalleryFilter> = _activeFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedItemIds: StateFlow<Set<String>> = _selectedItemIds.asStateFlow()

    val isMultiSelectMode: StateFlow<Boolean> = _selectedItemIds.map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private var currentOffset = 0
    private var isEndReached = false
    private var isLoadingPage = false

    val backedUpItems: StateFlow<List<UploadedItemEntity>> = dao.getAllFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val uploadQueue: StateFlow<List<UploadQueueItem>> = uploadRepository.queue
    private val _thumbnailMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val viewerStateMap: StateFlow<Map<String, ViewerFileState>> = viewerRepository.stateMap

    private val _downloadResult = MutableSharedFlow<DownloadResult>(extraBufferCapacity = 1)
    val downloadResult: SharedFlow<DownloadResult> = _downloadResult.asSharedFlow()

    init {
        loadNextPage()

        if (hasMediaPermissions()) {
            viewModelScope.launch {
                mediaRepository.startFullScan(forceShowProgress = false)
                currentOffset = 0
                isEndReached = false
                loadNextPage()
            }
            mediaRepository.registerObserver(viewModelScope) {
                viewModelScope.launch {
                    mediaRepository.startFullScan(forceShowProgress = false)
                    currentOffset = 0
                    isEndReached = false
                    loadNextPage()
                }
            }
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            backedUpItems.collect { entities ->
                val newThumbs = mutableMapOf<String, String>()
                entities.forEach { entity ->
                    if (!_thumbnailMap.value.containsKey(entity.indexId)) {
                        val thumbFile = thumbnailRepository.getOrFetchThumbnailFile(entity)
                        if (thumbFile != null && thumbFile.exists()) {
                            newThumbs[entity.indexId] = thumbFile.absolutePath
                        }
                    }
                }
                if (newThumbs.isNotEmpty()) {
                    _thumbnailMap.update { current -> current + newThumbs }
                }
            }
        }
    }

    fun hasMediaPermissions(): Boolean = mediaRepository.hasMediaPermissions()

    fun startMediaScan() {
        viewModelScope.launch {
            mediaRepository.startFullScan(forceShowProgress = true)
            currentOffset = 0
            isEndReached = false
            _unifiedItems.value = emptyList()
            loadNextPage()
        }
    }

    fun setFilter(filter: GalleryFilter) {
        _activeFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleItemSelection(indexId: String) {
        _selectedItemIds.update { current ->
            if (current.contains(indexId)) current - indexId else current + indexId
        }
    }

    fun selectAll() {
        _selectedItemIds.value = filteredCombinedItems.value.map { it.indexId }.toSet()
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun createAlbum(title: String) {
        viewModelScope.launch {
            val album = albumRepository.createAlbum(title)
            if (_selectedItemIds.value.isNotEmpty()) {
                albumRepository.addMediaToAlbum(album.albumId, _selectedItemIds.value.toList())
                clearSelection()
            }
        }
    }

    fun addSelectedToAlbum(albumId: String) {
        viewModelScope.launch {
            if (_selectedItemIds.value.isNotEmpty()) {
                albumRepository.addMediaToAlbum(albumId, _selectedItemIds.value.toList())
                clearSelection()
            }
        }
    }

    fun loadNextPage(pageSize: Int = 60) {
        if (isLoadingPage || isEndReached) return
        viewModelScope.launch {
            isLoadingPage = true
            val page = unifiedMediaRepository.getUnifiedMediaPaged(pageSize, currentOffset)
            if (page.isEmpty()) {
                isEndReached = true
            } else {
                currentOffset += page.size
                _unifiedItems.update { current -> (current + page).distinctBy { it.id } }
            }
            isLoadingPage = false
        }
    }

    val combinedItems: StateFlow<List<VaultGalleryItem>> = combine(
        backedUpItems,
        _unifiedItems,
        uploadQueue,
        _thumbnailMap,
    ) { roomEntities, unifiedList, queueItems, thumbs ->
        val result = LinkedHashMap<String, VaultGalleryItem>()

        // 1. Backed up items from UploadedItemDao (guarantees all Telegram cloud uploads are shown)
        for (entity in roomEntities) {
            result[entity.indexId] = VaultGalleryItem(
                indexId = entity.indexId,
                localUri = entity.localUri,
                displayName = entity.displayName,
                sizeBytes = entity.sizeBytes,
                mimeType = entity.mimeType,
                isUploading = false,
                isBackedUp = true,
                backupStatus = BackupStatus.BACKED_UP,
                entity = entity,
                thumbnailPath = thumbs[entity.indexId] ?: entity.localUri,
                takenAt = entity.takenAt,
            )
        }

        // 2. Unified media items (local device media + merged cloud items)
        for (unifiedItem in unifiedList) {
            if (!result.containsKey(unifiedItem.id)) {
                val isBackedUp = unifiedItem.backupStatus == BackupStatus.BACKED_UP ||
                        (unifiedItem.telegramChatId != null && unifiedItem.telegramMessageId != null && unifiedItem.telegramMessageId != 0L)

                val effectiveStatus = when {
                    isBackedUp -> BackupStatus.BACKED_UP
                    unifiedItem.backupStatus == BackupStatus.REMOTE_ONLY -> BackupStatus.REMOTE_ONLY
                    else -> BackupStatus.LOCAL_ONLY
                }

                val entity = if (isBackedUp && unifiedItem.telegramChatId != null && unifiedItem.telegramMessageId != null) {
                    UploadedItemEntity(
                        indexId = unifiedItem.id,
                        localUri = unifiedItem.localUri?.toString() ?: "",
                        displayName = unifiedItem.filename,
                        sizeBytes = unifiedItem.sizeBytes,
                        mimeType = unifiedItem.mimeType,
                        sha256 = unifiedItem.sha256,
                        takenAt = unifiedItem.createdAt,
                        telegramChatId = unifiedItem.telegramChatId,
                        telegramMessageId = unifiedItem.telegramMessageId,
                        uploadedAt = unifiedItem.uploadedAt ?: unifiedItem.modifiedAt,
                        thumbnailRemoteId = unifiedItem.thumbnailRemoteId ?: "",
                    )
                } else null

                result[unifiedItem.id] = VaultGalleryItem(
                    indexId = unifiedItem.id,
                    localUri = unifiedItem.localUri?.toString() ?: unifiedItem.devicePath ?: "",
                    displayName = unifiedItem.filename,
                    sizeBytes = unifiedItem.sizeBytes,
                    mimeType = unifiedItem.mimeType,
                    isUploading = false,
                    isBackedUp = isBackedUp,
                    backupStatus = effectiveStatus,
                    entity = entity,
                    thumbnailPath = thumbs[unifiedItem.id] ?: unifiedItem.devicePath,
                    takenAt = unifiedItem.createdAt,
                )
            }
        }

        // 3. Active queue items
        val roomIndexIds = roomEntities.map { it.indexId }.toSet()
        val roomUris = roomEntities.map { it.localUri }.toSet()

        for (queueItem in queueItems) {
            if (queueItem.state is UploadState.Queued || queueItem.state is UploadState.Uploading) {
                if (queueItem.id !in roomIndexIds && queueItem.mediaItem.uri.toString() !in roomUris) {
                    result[queueItem.id] = VaultGalleryItem(
                        indexId = queueItem.id,
                        localUri = queueItem.mediaItem.uri.toString(),
                        displayName = queueItem.mediaItem.displayName,
                        sizeBytes = queueItem.mediaItem.sizeBytes,
                        mimeType = queueItem.mediaItem.mimeType,
                        isUploading = true,
                        isBackedUp = false,
                        backupStatus = BackupStatus.UPLOADING,
                        entity = null,
                        thumbnailPath = null,
                        takenAt = System.currentTimeMillis(),
                    )
                }
            }
        }

        result.values.toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val filteredCombinedItems: StateFlow<List<VaultGalleryItem>> = combine(
        combinedItems,
        _activeFilter,
        _searchQuery,
    ) { items, filter, query ->
        items.filter { item ->
            val matchesQuery = query.isBlank() || item.displayName.contains(query, ignoreCase = true) || item.mimeType.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                GalleryFilter.ALL -> true
                GalleryFilter.PHOTOS -> !item.isVideo
                GalleryFilter.VIDEOS -> item.isVideo
                GalleryFilter.LOCAL_ONLY -> !item.isBackedUp
                GalleryFilter.CLOUD_ONLY -> item.isBackedUp && item.localUri.isBlank()
                GalleryFilter.BACKED_UP -> item.isBackedUp
                GalleryFilter.PENDING_BACKUP -> item.isUploading
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val dateGroupList: StateFlow<List<DateMediaGroup>> = filteredCombinedItems.map { items ->
        items
            .groupBy { item -> getItemDate(item.takenAt) }
            .toSortedMap(compareByDescending { it })
            .map { (dateKey, itemList) ->
                DateMediaGroup(
                    dateKey = dateKey,
                    headerTitle = formatDateHeader(dateKey),
                    items = itemList,
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun findByIndexId(indexId: String): VaultGalleryItem? =
        combinedItems.value.firstOrNull { it.indexId == indexId }

    fun fetchFullResolution(indexId: String) {
        val entity = findByIndexId(indexId)?.entity ?: return
        viewModelScope.launch {
            viewerRepository.fetchFullResolution(entity)
        }
    }

    fun downloadToGallery(indexId: String) {
        val entity = findByIndexId(indexId)?.entity ?: return
        val state = viewerRepository.stateFor(indexId)
        val localPath = (state as? ViewerFileState.Ready)?.localPath ?: return
        viewModelScope.launch {
            val uri: Uri? = viewerRepository.saveToMediaStore(entity, localPath)
            if (uri != null) {
                _downloadResult.tryEmit(DownloadResult.Success)
            } else {
                _downloadResult.tryEmit(DownloadResult.Failure("Failed to save to gallery"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaRepository.unregisterObserver()
    }
}
