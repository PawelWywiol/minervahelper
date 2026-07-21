package com.pawelwywiol.minervahelper.data.placement

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * Where downloaded ROMs are put: one root folder chosen by the user, and one subfolder per platform.
 *
 * The root is a Storage Access Framework tree rather than a path. The app has no write access to the
 * emulator's own folders otherwise, and asking for all-files access to get one would be a far larger
 * permission than this needs.
 */
class Destinations(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("destinations", Context.MODE_PRIVATE)
    private val platforms = PlatformFolders(app)

    var root by mutableStateOf(prefs.getString(ROOT_KEY, null)?.let(Uri::parse))
        private set

    /**
     * The grant has to be taken with the exact flags the picker returned, and only in the callback —
     * a tree URI stored without it is unusable after the next restart.
     */
    fun rememberRoot(uri: Uri, flags: Int) {
        app.contentResolver.takePersistableUriPermission(
            uri,
            flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        )
        prefs.edit { putString(ROOT_KEY, uri.toString()) }
        root = uri
    }

    fun folderFor(collection: String, platformKey: String): String =
        prefs.getString(folderKey(collection, platformKey), null)
            ?: platforms.folderFor(collection, platformKey)

    /**
     * The archive's own slug for a platform, ignoring where the user chose to put it. Naming and
     * icons must not change just because someone renamed a folder.
     */
    fun slugFor(collection: String, platformKey: String): String =
        platforms.folderFor(collection, platformKey)

    fun setFolder(collection: String, platformKey: String, folder: String) {
        prefs.edit { putString(folderKey(collection, platformKey), folder.trim()) }
        revision++
    }

    fun isCustom(collection: String, platformKey: String): Boolean =
        prefs.contains(folderKey(collection, platformKey))

    fun isMapped(collection: String, platformKey: String): Boolean =
        platforms.isMapped(collection, platformKey)

    /** Bumped on every override so the mapping screen redraws. */
    var revision by mutableStateOf(0)
        private set

    private fun folderKey(collection: String, platformKey: String) = "folder/$collection/$platformKey"

    companion object {
        private const val ROOT_KEY = "root"

        @Volatile
        private var instance: Destinations? = null

        fun get(context: Context): Destinations =
            instance ?: synchronized(this) {
                instance ?: Destinations(context).also { instance = it }
            }
    }
}
