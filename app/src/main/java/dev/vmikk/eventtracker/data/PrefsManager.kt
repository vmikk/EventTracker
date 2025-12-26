package dev.vmikk.eventtracker.data

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "prefs"
    
    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // Calendar settings
    const val KEY_CALENDAR_MAX_MARKERS = "calendar_max_markers"
    const val DEFAULT_CALENDAR_MAX_MARKERS = 3
    
    // Backup settings
    const val KEY_DAILY_BACKUP_ENABLED = "daily_backup_enabled"
    const val DEFAULT_DAILY_BACKUP_ENABLED = false
}

