package org.onekash.kashcal.domain.backup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.onekash.kashcal.data.preferences.KashCalDataStore
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues, cancels, or reconciles the periodic WorkManager job for automated settings backups (FORK:auto-backup).
 */
@Singleton
class AutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: KashCalDataStore
) {
    suspend fun reconcile() {
        try {
            val enabled = dataStore.autoBackupEnabled.first()
            val uri = dataStore.autoBackupTreeUri.first()
            val days = dataStore.autoBackupIntervalDays.first()
            if (enabled && !uri.isNullOrBlank()) {
                schedule(days)
            } else if (!enabled) {
                cancel()
            }
        } catch (e: Exception) {
            Log.w("AutoBackupScheduler", "Failed to reconcile auto backup job", e)
        }
    }

    fun schedule(intervalDays: Int = 1) {
        val safeDays = intervalDays.coerceAtLeast(1)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            safeDays.toLong(),
            TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(AutoBackupWorker.WORK_NAME)
    }
}
