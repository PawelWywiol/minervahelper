package com.pawelwywiol.minervahelper.data.catalog

import android.content.Context

/**
 * The readable name and the medium of a platform, keyed by its ES-DE slug.
 *
 * A list row cannot show the archive's raw platform key — "Nintendo/Famicom & Entertainment
 * System" is longer than most ROM names and repeats the vendor. The slug already resolved for
 * folder placement gives us "Nintendo Entertainment System" instead, and the medium the software
 * came on gives the row its icon.
 *
 * Media, not manufacturer logos: those are trademarks, the comprehensive sets published online
 * carry no licence, and nine shapes cover the whole archive — including the fifth of it that is
 * scans and disk images with no console behind them.
 */
class Systems(context: Context) {

    private val app = context.applicationContext
    private val entries: Map<String, Entry> by lazy { load() }

    data class Entry(val displayName: String, val media: String)

    /**
     * The platform key wins over the slug when it is itself a known system.
     *
     * Whole collections are mapped to one slug for placement — everything under FinalBurn Neo goes
     * in `arcade`, which is right for the folder. It is wrong for a row: FinalBurn Neo's own
     * `megadrive` platform would read "Arcade" on a screen titled "megadrive". The key resolves it
     * to "Sega Mega Drive" and a cartridge, while the file still lands in `arcade`.
     */
    private fun entryFor(platformKey: String, slug: String): Entry? =
        entries[platformKey.lowercase()] ?: entries[slug]

    /** Falls back to the raw platform key, for the two thirds of the archive with no system. */
    fun labelFor(platformKey: String, slug: String): String =
        entryFor(platformKey, slug)?.displayName ?: platformKey

    fun mediaFor(collection: String, platformKey: String, slug: String): String =
        entryFor(platformKey, slug)?.media
        // bitsavers is 93k manuals and scans; a cartridge icon on a PDF is worse than a neutral one.
            ?: if (collection == "bitsavers") "document" else "unknown"

    private fun load(): Map<String, Entry> =
        app.assets.open("systems.csv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val fields = line.split(',')
                if (fields.size < 3) null else fields[0] to Entry(fields[1], fields[2])
            }.toMap()
        }

    companion object {
        @Volatile
        private var instance: Systems? = null

        fun get(context: Context): Systems =
            instance ?: synchronized(this) {
                instance ?: Systems(context.applicationContext).also { instance = it }
            }
    }
}
