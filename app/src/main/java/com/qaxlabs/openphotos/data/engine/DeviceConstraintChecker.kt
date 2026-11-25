package com.qaxlabs.openphotos.data.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import com.qaxlabs.openphotos.data.BackupPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceConstraintChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupPreferences: BackupPreferences,
) {
    /**
     * Checks if current network connection satisfies user backup preferences.
     */
    suspend fun isNetworkAllowed(): Boolean {
        val wifiOnly = backupPreferences.wifiOnlyFlow.first()
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false

        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return if (wifiOnly) {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    /**
     * Checks battery status and power save mode according to user preference.
     */
    suspend fun isBatteryAllowed(): Boolean {
        val batteryAware = backupPreferences.batteryAwareFlow.first()
        if (!batteryAware) return true

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isPowerSaveMode == true) return false

        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()) else 100f

        return isCharging || batteryPct > 15f
    }

    /**
     * Evaluates all device constraints.
     */
    suspend fun canExecuteBackup(): Boolean {
        return isNetworkAllowed() && isBatteryAllowed()
    }
}
