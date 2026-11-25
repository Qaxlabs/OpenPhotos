package com.qaxlabs.openphotos.data.engine

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker executing adaptive background backup tasks.
 */
@HiltWorker
class AdaptiveBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: AdaptiveUploadScheduler,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AdaptiveBackupWorker"
        const val WORK_NAME = "openphotos_adaptive_backup_work"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "AdaptiveBackupWorker triggered by WorkManager.")
        return try {
            val executedCount = scheduler.executePendingTasks()
            Log.i(TAG, "AdaptiveBackupWorker executed $executedCount tasks.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "AdaptiveBackupWorker encountered error: ${e.message}", e)
            Result.retry()
        }
    }
}
