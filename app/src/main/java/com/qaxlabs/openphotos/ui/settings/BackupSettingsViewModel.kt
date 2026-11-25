package com.qaxlabs.openphotos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qaxlabs.openphotos.data.BackupPreferences
import com.qaxlabs.openphotos.data.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the auto-backup toggle in [SettingsScreen].
 *
 * ARCHITECTURE CONTRACT (tech_stack.md §3):
 * Does NOT call TDLib or Room directly. Delegates to [BackupPreferences]
 * (DataStore) and [WorkScheduler] (WorkManager).
 */
@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    private val backupPreferences: BackupPreferences,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    val autoBackupEnabled: StateFlow<Boolean> =
        backupPreferences.autoBackupEnabledFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = true,
            )

    val photosEnabled: StateFlow<Boolean> =
        backupPreferences.photosBackupEnabledFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = true,
            )

    val videosEnabled: StateFlow<Boolean> =
        backupPreferences.videosBackupEnabledFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = true,
            )

    val wifiOnly: StateFlow<Boolean> =
        backupPreferences.wifiOnlyFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = true,
            )

    val batteryAware: StateFlow<Boolean> =
        backupPreferences.batteryAwareFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = true,
            )

    val largeVideoThresholdMb: StateFlow<Int> =
        backupPreferences.largeVideoThresholdMbFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 500,
            )

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            backupPreferences.setAutoBackupEnabled(enabled)
            if (enabled) {
                workScheduler.schedule()
            } else {
                workScheduler.cancel()
            }
        }
    }

    fun setPhotosEnabled(enabled: Boolean) {
        viewModelScope.launch {
            backupPreferences.setPhotosBackupEnabled(enabled)
        }
    }

    fun setVideosEnabled(enabled: Boolean) {
        viewModelScope.launch {
            backupPreferences.setVideosBackupEnabled(enabled)
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            backupPreferences.setWifiOnly(wifiOnly)
        }
    }

    fun setBatteryAware(batteryAware: Boolean) {
        viewModelScope.launch {
            backupPreferences.setBatteryAware(batteryAware)
        }
    }

    fun setLargeVideoThresholdMb(thresholdMb: Int) {
        viewModelScope.launch {
            backupPreferences.setLargeVideoThresholdMb(thresholdMb)
        }
    }
}
