package com.pawelwywiol.minervahelper.data.placement

import android.content.Context

/**
 * Maps a catalogue platform onto the folder name an emulator frontend expects.
 *
 * Two thirds of the archive's 1,592 platforms have no such folder — they are documentation, scans,
 * or machines nobody emulates. Those keep a cleaned-up name taken from the archive instead of being
 * refused (R18, D5): a ROM in an oddly named folder is still playable, an undownloadable one is not.
 */
class PlatformFolders(context: Context) {

    private val app = context.applicationContext
    private val slugs: Map<String, String> by lazy { load() }

    fun folderFor(collection: String, platformKey: String): String =
        slugs[key(collection, platformKey)] ?: fallback(platformKey)

    fun isMapped(collection: String, platformKey: String): Boolean =
        key(collection, platformKey) in slugs

    private fun load(): Map<String, String> =
        app.assets.open("platform_map.csv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val fields = splitCsv(line)
                val slug = fields.getOrNull(2).orEmpty()
                if (fields.size < 3 || slug.isEmpty()) null
                else key(fields[0], fields[1]) to slug
            }.toMap()
        }

    private fun key(collection: String, platformKey: String) = "$collection/$platformKey"

    /** Platform keys contain commas ("Commodore/C16, C116 & Plus-4"), so those rows are quoted. */
    private fun splitCsv(line: String): List<String> = buildList {
        var i = 0
        while (i <= line.length) {
            if (i < line.length && line[i] == '"') {
                val end = line.indexOf('"', i + 1).takeIf { it >= 0 } ?: line.length
                add(line.substring(i + 1, end))
                i = end + 2
            } else {
                val end = line.indexOf(',', i).takeIf { it >= 0 } ?: line.length
                add(line.substring(i, end))
                i = end + 1
            }
        }
    }

    private fun fallback(platformKey: String) = platformKey
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifEmpty { "unsorted" }
}
