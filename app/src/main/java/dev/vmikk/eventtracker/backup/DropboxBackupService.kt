package dev.vmikk.eventtracker.backup

import android.content.Context
import com.dropbox.core.DbxException
import com.dropbox.core.NetworkIOException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.ListFolderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

sealed class BackupResult {
    data class Success(val file: File? = null) : BackupResult()
    data class Error(val message: String, val type: ErrorType) : BackupResult()
    
    enum class ErrorType {
        NETWORK,
        AUTH,
        FILE,
        UNKNOWN
    }
}

object DropboxBackupService {

    private const val BACKUP_DIR = "/backups"

    suspend fun uploadBackup(context: Context, encryptedBackup: File, keepLast: Int = 30): BackupResult {
        val accessToken = DropboxAuthManager.getValidAccessToken(context) ?: 
            return BackupResult.Error("Not authenticated", BackupResult.ErrorType.AUTH)
        
        return try {
            withContext(Dispatchers.IO) {
                val client = DropboxClientFactory.create(accessToken)
                
                try {
                    client.files().createFolderV2(BACKUP_DIR)
                } catch (e: DbxException) {
                    // ignore if already exists (409 conflict) - check error message
                    val message = e.message ?: ""
                    if (!message.contains("conflict", ignoreCase = true)) throw e
                }

                val destPath = "$BACKUP_DIR/${encryptedBackup.name}"
                FileInputStream(encryptedBackup).use { input ->
                    client.files().uploadBuilder(destPath).uploadAndFinish(input)
                }

                enforceRetention(client, keepLast)
            }
            BackupResult.Success()
        } catch (e: NetworkIOException) {
            android.util.Log.e("DropboxBackupService", "Network error during upload", e)
            BackupResult.Error("Network error. Please check your connection.", BackupResult.ErrorType.NETWORK)
        } catch (e: DbxException) {
            android.util.Log.e("DropboxBackupService", "Dropbox API error during upload", e)
            val message = e.message ?: ""
            when {
                message.contains("401", ignoreCase = true) || 
                message.contains("403", ignoreCase = true) ||
                message.contains("unauthorized", ignoreCase = true) ||
                message.contains("forbidden", ignoreCase = true) -> 
                    BackupResult.Error("Authentication failed. Please reconnect to Dropbox.", BackupResult.ErrorType.AUTH)
                else -> BackupResult.Error("Upload failed. Please try again.", BackupResult.ErrorType.UNKNOWN)
            }
        } catch (e: IOException) {
            android.util.Log.e("DropboxBackupService", "File I/O error during upload", e)
            BackupResult.Error("File error. Please try again.", BackupResult.ErrorType.FILE)
        } catch (e: Exception) {
            android.util.Log.e("DropboxBackupService", "Unexpected error during upload", e)
            BackupResult.Error("Upload failed. Please try again.", BackupResult.ErrorType.UNKNOWN)
        }
    }

    suspend fun downloadLatestBackup(context: Context): BackupResult {
        val accessToken = DropboxAuthManager.getValidAccessToken(context) ?: 
            return BackupResult.Error("Not authenticated", BackupResult.ErrorType.AUTH)

        return try {
            val file = withContext(Dispatchers.IO) {
                val client = DropboxClientFactory.create(accessToken)
                val files = listBackups(client)
                val latest = files.maxByOrNull { it.serverModified.time } ?: return@withContext null

                val outDir = File(context.cacheDir, "backups").apply { mkdirs() }
                val outFile = File(outDir, latest.name)
                FileOutputStream(outFile).use { out ->
                    client.files().downloadBuilder(latest.pathLower).download(out)
                }
                outFile
            }
            if (file != null) {
                BackupResult.Success(file)
            } else {
                BackupResult.Error("No backups found", BackupResult.ErrorType.FILE)
            }
        } catch (e: NetworkIOException) {
            android.util.Log.e("DropboxBackupService", "Network error during download", e)
            BackupResult.Error("Network error. Please check your connection.", BackupResult.ErrorType.NETWORK)
        } catch (e: DbxException) {
            android.util.Log.e("DropboxBackupService", "Dropbox API error during download", e)
            val message = e.message ?: ""
            when {
                message.contains("401", ignoreCase = true) || 
                message.contains("403", ignoreCase = true) ||
                message.contains("unauthorized", ignoreCase = true) ||
                message.contains("forbidden", ignoreCase = true) -> 
                    BackupResult.Error("Authentication failed. Please reconnect to Dropbox.", BackupResult.ErrorType.AUTH)
                message.contains("404", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true) -> 
                    BackupResult.Error("No backups found", BackupResult.ErrorType.FILE)
                else -> BackupResult.Error("Download failed. Please try again.", BackupResult.ErrorType.UNKNOWN)
            }
        } catch (e: IOException) {
            android.util.Log.e("DropboxBackupService", "File I/O error during download", e)
            BackupResult.Error("File error. Please try again.", BackupResult.ErrorType.FILE)
        } catch (e: Exception) {
            android.util.Log.e("DropboxBackupService", "Unexpected error during download", e)
            BackupResult.Error("Download failed. Please try again.", BackupResult.ErrorType.UNKNOWN)
        }
    }

    private fun listBackups(client: com.dropbox.core.v2.DbxClientV2): List<FileMetadata> {
        val result = try {
            client.files().listFolder(BACKUP_DIR)
        } catch (_: Exception) {
            return emptyList()
        }
        return collectAllFiles(client, result)
            .filter { it.name.endsWith(".etbak") }
    }

    private fun collectAllFiles(client: com.dropbox.core.v2.DbxClientV2, first: ListFolderResult): List<FileMetadata> {
        val out = mutableListOf<FileMetadata>()
        fun addEntries(res: ListFolderResult) {
            res.entries.forEach { meta ->
                when (meta) {
                    is FileMetadata -> out.add(meta)
                    is FolderMetadata -> Unit
                    else -> Unit
                }
            }
        }
        var res = first
        addEntries(res)
        while (res.hasMore) {
            res = client.files().listFolderContinue(res.cursor)
            addEntries(res)
        }
        return out
    }

    private fun enforceRetention(client: com.dropbox.core.v2.DbxClientV2, keepLast: Int) {
        if (keepLast <= 0) return
        val files = listBackups(client).sortedByDescending { it.serverModified.time }
        files.drop(keepLast).forEach { meta ->
            try {
                client.files().deleteV2(meta.pathLower)
            } catch (_: Exception) {
            }
        }
    }
}








