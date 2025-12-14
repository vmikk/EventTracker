package dev.vmikk.eventtracker.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DropboxBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!DropboxAuthManager.isConfigured(context) || !DropboxAuthManager.isLinked(context)) {
            return Result.failure()
        }

        return try {
            val encrypted = DatabaseBackup.createEncryptedBackup(context)
            DropboxBackupService.uploadBackup(context, encrypted, keepLast = 30)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}




