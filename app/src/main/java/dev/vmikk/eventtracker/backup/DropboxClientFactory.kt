package dev.vmikk.eventtracker.backup

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2

object DropboxClientFactory {
    fun create(accessToken: String): DbxClientV2 {
        val config = DbxRequestConfig.newBuilder("EventTracker/1.0").build()
        return DbxClientV2(config, accessToken)
    }
}




