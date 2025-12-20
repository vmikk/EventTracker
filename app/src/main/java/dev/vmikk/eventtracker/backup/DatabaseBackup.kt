package dev.vmikk.eventtracker.backup

import android.content.Context
import androidx.security.crypto.EncryptedFile
import dev.vmikk.eventtracker.data.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DatabaseBackup {

    private fun dbFiles(context: Context): List<File> {
        val db = context.getDatabasePath(AppDatabase.databaseName())
        return listOf(
            db,
            File(db.absolutePath + "-wal"),
            File(db.absolutePath + "-shm"),
        )
    }

    fun createEncryptedBackup(context: Context): File {
        val tmpDir = File(context.cacheDir, "backup_tmp").apply { mkdirs() }
        val zipFile = File(tmpDir, "db.zip")

        // Create plaintext zip
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            dbFiles(context).forEach { f ->
                if (!f.exists()) return@forEach
                FileInputStream(f).use { fis ->
                    zos.putNextEntry(ZipEntry(f.name))
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }

        val outDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val encryptedFile = File(outDir, "eventtracker-${System.currentTimeMillis()}.etbak")

        val ef = EncryptedFile.Builder(
            context,
            encryptedFile,
            SecureStore.masterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        ef.openFileOutput().use { out ->
            FileInputStream(zipFile).use { input -> input.copyTo(out) }
        }

        zipFile.delete()
        return encryptedFile
    }

    fun restoreFromEncryptedBackup(context: Context, encryptedBackup: File) {
        val tmpDir = File(context.cacheDir, "restore_tmp").apply { mkdirs() }
        val zipFile = File(tmpDir, "restore.zip")

        val ef = EncryptedFile.Builder(
            context,
            encryptedBackup,
            SecureStore.masterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        ef.openFileInput().use { input ->
            FileOutputStream(zipFile).use { out -> input.copyTo(out) }
        }

        // Close Room before replacing files
        AppDatabase.closeInstance()

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = when (entry.name) {
                    AppDatabase.databaseName() -> context.getDatabasePath(AppDatabase.databaseName())
                    AppDatabase.databaseName() + "-wal" -> File(context.getDatabasePath(AppDatabase.databaseName()).absolutePath + "-wal")
                    AppDatabase.databaseName() + "-shm" -> File(context.getDatabasePath(AppDatabase.databaseName()).absolutePath + "-shm")
                    else -> null
                }
                if (outFile != null) {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        zipFile.delete()
    }
}








