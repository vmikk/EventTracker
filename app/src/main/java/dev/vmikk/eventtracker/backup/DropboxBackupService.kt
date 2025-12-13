package dev.vmikk.eventtracker.backup

import android.content.Context
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.ListFolderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DropboxBackupService {

    private const val BACKUP_DIR = "/backups"

    suspend fun uploadBackup(context: Context, encryptedBackup: File, keepLast: Int = 30) {
        val accessToken = DropboxAuthManager.getValidAccessToken(context) ?: return
        val client = DropboxClientFactory.create(accessToken)

        withContext(Dispatchers.IO) {
            try {
                client.files().createFolderV2(BACKUP_DIR)
            } catch (_: Exception) {
                // ignore if already exists
            }

            val destPath = "$BACKUP_DIR/${encryptedBackup.name}"
            FileInputStream(encryptedBackup).use { input ->
                client.files().uploadBuilder(destPath).uploadAndFinish(input)
            }

            enforceRetention(client, keepLast)
        }
    }

    suspend fun downloadLatestBackup(context: Context): File? {
        val accessToken = DropboxAuthManager.getValidAccessToken(context) ?: return null
        val client = DropboxClientFactory.create(accessToken)

        return withContext(Dispatchers.IO) {
            val files = listBackups(client)
            val latest = files.maxByOrNull { it.serverModified.time } ?: return@withContext null

            val outDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val outFile = File(outDir, latest.name)
            FileOutputStream(outFile).use { out ->
                client.files().downloadBuilder(latest.pathLower).download(out)
            }
            outFile
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



