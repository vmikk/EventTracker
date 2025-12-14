package dev.vmikk.eventtracker.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import dev.vmikk.eventtracker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

object DropboxAuthManager {

    private const val PREF_REFRESH_TOKEN = "dropbox_refresh_token"
    private const val PREF_ACCESS_TOKEN = "dropbox_access_token"
    private const val PREF_EXPIRES_AT_MS = "dropbox_expires_at_ms"
    private const val PREF_CODE_VERIFIER = "dropbox_code_verifier"

    private val http = OkHttpClient()

    fun isConfigured(context: Context): Boolean {
        val key = context.getString(R.string.dropbox_app_key).trim()
        return key.isNotBlank() && key != "PUT_YOUR_DROPBOX_APP_KEY_HERE"
    }

    fun isLinked(context: Context): Boolean =
        SecureStore.prefs(context).getString(PREF_REFRESH_TOKEN, null).isNullOrBlank().not()

    fun startLink(context: Context) {
        val appKey = context.getString(R.string.dropbox_app_key).trim()
        val redirectUri = context.getString(R.string.dropbox_redirect_uri).trim()

        val verifier = generateCodeVerifier()
        val challenge = codeChallengeS256(verifier)

        SecureStore.prefs(context).edit()
            .putString(PREF_CODE_VERIFIER, verifier)
            .apply()

        val authUri = Uri.Builder()
            .scheme("https")
            .authority("www.dropbox.com")
            .appendPath("oauth2")
            .appendPath("authorize")
            .appendQueryParameter("client_id", appKey)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("token_access_type", "offline")
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("redirect_uri", redirectUri)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    suspend fun handleRedirect(context: Context, redirect: Uri): Boolean {
        val code = redirect.getQueryParameter("code") ?: return false
        val appKey = context.getString(R.string.dropbox_app_key).trim()
        val redirectUri = context.getString(R.string.dropbox_redirect_uri).trim()
        val verifier = SecureStore.prefs(context).getString(PREF_CODE_VERIFIER, null) ?: return false

        val responseJson = withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("client_id", appKey)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", verifier)
                .build()

            val req = Request.Builder()
                .url("https://api.dropbox.com/oauth2/token")
                .post(body)
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string()
            }
        } ?: return false

        val json = JSONObject(responseJson)
        val accessToken = json.getString("access_token")
        val refreshToken = json.optString("refresh_token", "")
        val expiresIn = json.optLong("expires_in", 0L)

        if (refreshToken.isBlank()) return false

        val expiresAt = System.currentTimeMillis() + expiresIn * 1000L
        SecureStore.prefs(context).edit()
            .putString(PREF_REFRESH_TOKEN, refreshToken)
            .putString(PREF_ACCESS_TOKEN, accessToken)
            .putLong(PREF_EXPIRES_AT_MS, expiresAt)
            .remove(PREF_CODE_VERIFIER)
            .apply()

        return true
    }

    suspend fun getValidAccessToken(context: Context): String? {
        val prefs = SecureStore.prefs(context)
        val now = System.currentTimeMillis()
        val access = prefs.getString(PREF_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(PREF_EXPIRES_AT_MS, 0L)
        if (!access.isNullOrBlank() && expiresAt > now + 60_000L) return access

        val refresh = prefs.getString(PREF_REFRESH_TOKEN, null) ?: return null
        val appKey = context.getString(R.string.dropbox_app_key).trim()

        val responseJson = withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("refresh_token", refresh)
                .add("grant_type", "refresh_token")
                .add("client_id", appKey)
                .build()

            val req = Request.Builder()
                .url("https://api.dropbox.com/oauth2/token")
                .post(body)
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string()
            }
        } ?: return null

        val json = JSONObject(responseJson)
        val newAccess = json.getString("access_token")
        val expiresIn = json.optLong("expires_in", 0L)
        val newExpiresAt = System.currentTimeMillis() + expiresIn * 1000L

        prefs.edit()
            .putString(PREF_ACCESS_TOKEN, newAccess)
            .putLong(PREF_EXPIRES_AT_MS, newExpiresAt)
            .apply()

        return newAccess
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun codeChallengeS256(verifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}




