package io.github.pawelwywiol.minervahelper.data.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Steps, not a slider: on a 4.7" screen driven by a d-pad, a handful of choices is quicker. */
val FONT_SCALES = listOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)

const val MIN_CONCURRENT_DOWNLOADS = 1
const val MAX_CONCURRENT_DOWNLOADS = 4

/**
 * The two settings that change how the app behaves rather than where files go.
 *
 * The default text size is legible on a phone held at reading distance; this device is a handheld
 * at arm's length, so the owner needs to make it bigger.
 */
class Preferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    var fontScale by mutableFloatStateOf(prefs.getFloat(FONT_SCALE_KEY, 1.0f))
        private set

    /**
     * Two by default: the swarms this archive relies on are found over DHT, and past three parallel
     * torrents the device spends more time on peer discovery than on the files.
     */
    var concurrentDownloads by mutableIntStateOf(prefs.getInt(CONCURRENT_KEY, 2))
        private set

    /**
     * Disc-based emulators cannot read a game out of a `.zip`, so an archive holding one has to be
     * unpacked before it is any use. On by default: an archive the emulator refuses to open looks
     * to the user like a failed download.
     */
    var unpackArchives by mutableStateOf(prefs.getBoolean(UNPACK_KEY, true))
        private set

    fun updateUnpackArchives(enabled: Boolean) {
        prefs.edit { putBoolean(UNPACK_KEY, enabled) }
        unpackArchives = enabled
    }

    fun updateFontScale(scale: Float) {
        prefs.edit { putFloat(FONT_SCALE_KEY, scale) }
        fontScale = scale
    }

    fun updateConcurrentDownloads(count: Int) {
        val bounded = count.coerceIn(MIN_CONCURRENT_DOWNLOADS, MAX_CONCURRENT_DOWNLOADS)
        prefs.edit { putInt(CONCURRENT_KEY, bounded) }
        concurrentDownloads = bounded
    }

    companion object {
        private const val FONT_SCALE_KEY = "font_scale"
        private const val CONCURRENT_KEY = "concurrent_downloads"
        private const val UNPACK_KEY = "unpack_archives"

        /** The worker and the UI must read the same value the moment it changes. */
        @Volatile
        private var instance: Preferences? = null

        fun get(context: Context): Preferences =
            instance ?: synchronized(this) {
                instance ?: Preferences(context.applicationContext).also { instance = it }
            }
    }
}
