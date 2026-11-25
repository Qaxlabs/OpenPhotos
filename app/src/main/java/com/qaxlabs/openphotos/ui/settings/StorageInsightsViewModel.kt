package com.qaxlabs.openphotos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qaxlabs.openphotos.data.StorageManagementRepository
import com.qaxlabs.openphotos.data.StorageStats
import com.qaxlabs.openphotos.data.UnifiedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageInsightsViewModel @Inject constructor(
    private val storageRepo: StorageManagementRepository,
) : ViewModel() {

    private val _stats = MutableStateFlow(StorageStats())
    val stats: StateFlow<StorageStats> = _stats.asStateFlow()

    private val _isFreeingSpace = MutableStateFlow(false)
    val isFreeingSpace: StateFlow<Boolean> = _isFreeingSpace.asStateFlow()

    init {
        loadStorageStats()
    }

    fun loadStorageStats() {
        viewModelScope.launch {
            _stats.value = storageRepo.calculateStorageStats()
        }
    }

    fun freeUpSpace() {
        viewModelScope.launch {
            _isFreeingSpace.value = true
            storageRepo.freeUpSpace()
            _stats.value = storageRepo.calculateStorageStats()
            _isFreeingSpace.value = false
        }
    }

    fun deleteDeviceCopy(item: UnifiedMediaItem) {
        viewModelScope.launch {
            storageRepo.deleteDeviceCopy(item)
            loadStorageStats()
        }
    }

    fun deleteCloudCopy(item: UnifiedMediaItem) {
        viewModelScope.launch {
            storageRepo.deleteCloudCopy(item)
            loadStorageStats()
        }
    }

    fun deleteEverywhere(item: UnifiedMediaItem) {
        viewModelScope.launch {
            storageRepo.deleteEverywhere(item)
            loadStorageStats()
        }
    }
}
