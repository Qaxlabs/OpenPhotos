package com.qaxlabs.openphotos.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "openphotos_backup_prefs")

/**
 * User-facing preferences for continuous automatic backup.
 */
@Singleton
class BackupPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_AUTO_BACKUP_ENABLED     = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_PHOTOS_BACKUP_ENABLED   = booleanPreferencesKey("photos_backup_enabled")
        private val KEY_VIDEOS_BACKUP_ENABLED   = booleanPreferencesKey("videos_backup_enabled")
        private val KEY_WIFI_ONLY               = booleanPreferencesKey("wifi_only")
        private val KEY_BATTERY_AWARE           = booleanPreferencesKey("battery_aware")
        private val KEY_LARGE_VIDEO_THRESHOLD   = intPreferencesKey("large_video_threshold_mb")
        private val KEY_LAST_SEEN_TS            = longPreferencesKey("last_seen_ts_secs")
    }

    // ── Auto-backup toggle ────────────────────────────────────────────────────

    val autoBackupEnabledFlow: Flow<Boolean> =
        context.backupDataStore.data.map { it[KEY_AUTO_BACKUP_ENABLED] ?: true }

    suspend fun isAutoBackupEnabled(): Boolean =
        context.backupDataStore.data.first()[KEY_AUTO_BACKUP_ENABLED] ?: true

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.backupDataStore.edit { it[KEY_AUTO_BACKUP_ENABLED] = enabled }
    }

    // ── Media Type Preferences ────────────────────────────────────────────────

    val photosBackupEnabledFlow: Flow<Boolean> =
        context.backupDataStore.data.map { it[KEY_PHOTOS_BACKUP_ENABLED] ?: true }

    suspend fun isPhotosBackupEnabled(): Boolean =
        context.backupDataStore.data.first()[KEY_PHOTOS_BACKUP_ENABLED] ?: true

    suspend fun setPhotosBackupEnabled(enabled: Boolean) {
        context.backupDataStore.edit { it[KEY_PHOTOS_BACKUP_ENABLED] = enabled }
    }

    val videosBackupEnabledFlow: Flow<Boolean> =
        context.backupDataStore.data.map { it[KEY_VIDEOS_BACKUP_ENABLED] ?: true }

    suspend fun isVideosBackupEnabled(): Boolean =
        context.backupDataStore.data.first()[KEY_VIDEOS_BACKUP_ENABLED] ?: true

    suspend fun setVideosBackupEnabled(enabled: Boolean) {
        context.backupDataStore.edit { it[KEY_VIDEOS_BACKUP_ENABLED] = enabled }
    }

    // ── Network & Battery Constraints ─────────────────────────────────────────

    val wifiOnlyFlow: Flow<Boolean> =
        context.backupDataStore.data.map { it[KEY_WIFI_ONLY] ?: true }

    suspend fun isWifiOnly(): Boolean =
        context.backupDataStore.data.first()[KEY_WIFI_ONLY] ?: true

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        context.backupDataStore.edit { it[KEY_WIFI_ONLY] = wifiOnly }
    }

    val batteryAwareFlow: Flow<Boolean> =
        context.backupDataStore.data.map { it[KEY_BATTERY_AWARE] ?: true }

    suspend fun isBatteryAware(): Boolean =
        context.backupDataStore.data.first()[KEY_BATTERY_AWARE] ?: true

    suspend fun setBatteryAware(batteryAware: Boolean) {
        context.backupDataStore.edit { it[KEY_BATTERY_AWARE] = batteryAware }
    }

    val largeVideoThresholdMbFlow: Flow<Int> =
        context.backupDataStore.data.map { it[KEY_LARGE_VIDEO_THRESHOLD] ?: 500 }

    suspend fun getLargeVideoThresholdMb(): Int =
        context.backupDataStore.data.first()[KEY_LARGE_VIDEO_THRESHOLD] ?: 500

    suspend fun setLargeVideoThresholdMb(thresholdMb: Int) {
        context.backupDataStore.edit { it[KEY_LARGE_VIDEO_THRESHOLD] = thresholdMb }
    }

    // ── Watermark ─────────────────────────────────────────────────────────────

    suspend fun getLastSeenTimestamp(): Long =
        context.backupDataStore.data.first()[KEY_LAST_SEEN_TS] ?: 0L

    suspend fun setLastSeenTimestamp(ts: Long) {
        context.backupDataStore.edit { it[KEY_LAST_SEEN_TS] = ts }
    }
}
