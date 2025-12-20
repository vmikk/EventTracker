package dev.vmikk.eventtracker.backup

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal object SecureStore {
    private const val PREFS_NAME = "secure_prefs"

    fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
}








