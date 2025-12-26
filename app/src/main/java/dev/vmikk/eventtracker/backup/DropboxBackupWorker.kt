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
            val result = DropboxBackupService.uploadBackup(context, encrypted, keepLast = 30)
            when (result) {
                is dev.vmikk.eventtracker.backup.BackupResult.Success -> Result.success()
                is dev.vmikk.eventtracker.backup.BackupResult.Error -> {
                    android.util.Log.e("DropboxBackupWorker", "Backup failed: ${result.message}")
                    when (result.type) {
                        dev.vmikk.eventtracker.backup.BackupResult.ErrorType.NETWORK -> Result.retry()
                        dev.vmikk.eventtracker.backup.BackupResult.ErrorType.AUTH -> Result.failure()
                        else -> Result.retry()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DropboxBackupWorker", "Unexpected error during backup", e)
            Result.retry()
        }
    }
}








