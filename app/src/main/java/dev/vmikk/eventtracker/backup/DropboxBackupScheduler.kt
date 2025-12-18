package dev.vmikk.eventtracker.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DropboxBackupScheduler {
    private const val UNIQUE_PERIODIC_NAME = "dropbox_periodic_backup"
    private const val UNIQUE_IMMEDIATE_NAME = "dropbox_immediate_backup"

    fun setDailyEnabled(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(UNIQUE_PERIODIC_NAME)
            return
        }
        val req = PeriodicWorkRequestBuilder<DropboxBackupWorker>(1, TimeUnit.DAYS)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun enqueueImmediate(context: Context) {
        val wm = WorkManager.getInstance(context)
        val req = OneTimeWorkRequestBuilder<DropboxBackupWorker>().build()
        wm.enqueueUniqueWork(UNIQUE_IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, req)
    }
}







