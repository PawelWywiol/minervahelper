package io.github.pawelwywiol.minervahelper.data.web

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * What the settings screen knows about the browser remote. The server itself lives in a service so
 * it can outlive the screen; this is the one place both agree on.
 */
object WebRemote {

    /** The address to type into a browser; null while the server is not listening. */
    var url by mutableStateOf<String?>(null)
        internal set

    /** Why it is not running, in words the owner can act on. */
    var problem by mutableStateOf<String?>(null)
        internal set

    val running: Boolean get() = url != null

    fun start(context: Context) {
        problem = null
        ContextCompat.startForegroundService(context, Intent(context, WebServerService::class.java))
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, WebServerService::class.java))
    }
}
