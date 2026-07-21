package com.pawelwywiol.minervahelper.data.catalog

import android.content.Context

/**
 * The readable name and the icon of a platform, keyed by its ES-DE slug.
 *
 * A list row cannot show the archive's raw platform key — "Nintendo/Famicom & Entertainment
 * System" is longer than most ROM names and repeats the vendor. The slug already resolved for
 * folder placement gives us "Nintendo Entertainment System" instead.
 *
 * The icon is two independent facts, because one of them alone would not identify anything:
 * [Icon.shape] is what the machine is, [Icon.family] is who made it — and the family is what the
 * colour carries. That split is why a Game Boy and an NES share Nintendo red while looking
 * different, and why a Sega NAOMI board reads as an arcade cabinet in Sega's blue.
 *
 * Silhouettes, not logos: manufacturer marks are trademarks and the comprehensive sets published
 * online carry no licence at all. A machine's outline plus the vendor's colour is recognisable
 * without borrowing anyone's mark.
 */
class Systems(context: Context) {

    private val app = context.applicationContext
    private val entries: Map<String, Entry> by lazy { load() }

    private data class Entry(val displayName: String, val shape: String, val family: String)

    data class Icon(val shape: String, val family: String)

    /**
     * The platform key wins over the slug when it is itself a known system.
     *
     * Whole collections are mapped to one slug for placement — everything under FinalBurn Neo goes
     * in `arcade`, which is right for the folder. It is wrong for a row: FinalBurn Neo's own
     * `megadrive` platform would read "Arcade" on a screen titled "megadrive". The key resolves it
     * to "Sega Mega Drive" and Sega blue, while the file still lands in `arcade`.
     */
    private fun entryFor(platformKey: String, slug: String): Entry? =
        entries[platformKey.lowercase()] ?: entries[slug]

    /** Falls back to the raw platform key, for the fifth of the archive with no system. */
    fun labelFor(platformKey: String, slug: String): String =
        entryFor(platformKey, slug)?.displayName ?: platformKey

    /**
     * Content with no system behind it — scans, manuals, media dumps — is identified by its file
     * type instead, in one neutral colour. A cartridge on a PDF would be a worse guess than none.
     */
    fun iconFor(collection: String, platformKey: String, slug: String, name: String): Icon =
        entryFor(platformKey, slug)?.let { Icon(it.shape, it.family) }
            ?: Icon(fileShape(collection, name), FILE_FAMILY)

    private fun fileShape(collection: String, name: String): String =
        FILE_SHAPES[name.substringAfterLast('.', "").lowercase()]
        // bitsavers is 93k scanned manuals; when the extension says nothing, "paper" still holds.
            ?: if (collection == "bitsavers") "text" else "file"

    private fun load(): Map<String, Entry> =
        app.assets.open("systems.csv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val fields = line.split(',')
                if (fields.size < 4) null
                else fields[0] to Entry(fields[1], fields[2], fields[3])
            }.toMap()
        }

    companion object {
        const val FILE_FAMILY = "file"

        private val FILE_SHAPES = mapOf(
            "image" to "jpg jpeg png gif bmp webp tif tiff tga pcx",
            "video" to "mp4 mkv avi mpg mpeg webm mov flv wmv",
            "audio" to "mp3 flac ogg wav m4a opus wma mid sid",
            "text" to "pdf txt doc docx epub djvu rtf md htm html nfo",
            "archive" to "zip 7z rar tar gz bz2 xz lha",
        ).flatMap { (shape, extensions) -> extensions.split(' ').map { it to shape } }.toMap()

        @Volatile
        private var instance: Systems? = null

        fun get(context: Context): Systems =
            instance ?: synchronized(this) {
                instance ?: Systems(context.applicationContext).also { instance = it }
            }
    }
}
