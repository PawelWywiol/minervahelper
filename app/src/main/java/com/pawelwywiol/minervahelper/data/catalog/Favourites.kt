package com.pawelwywiol.minervahelper.data.catalog

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.edit

/**
 * Favourites survive a catalog rebuild, so they cannot be stored as row ids — those are handed out
 * by import order and shift whenever hashes.db is re-imported, which would silently repoint every
 * favourite at a different ROM. The archive path (collection / platform / file name) is stable.
 */
class Favourites(context: Context) {

    private val prefs = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)
    private val keys = mutableStateListOf<String>().apply {
        addAll(prefs.getStringSet(STORE_KEY, emptySet()).orEmpty())
    }

    fun contains(rom: CatalogReader.Rom): Boolean = keyOf(rom) in keys

    fun toggle(rom: CatalogReader.Rom) {
        val key = keyOf(rom)
        if (!keys.remove(key)) keys.add(key)
        prefs.edit { putStringSet(STORE_KEY, keys.toSet()) }
    }

    fun all(): List<Key> = keys.sorted().mapNotNull { stored ->
        val parts = stored.split(SEPARATOR)
        if (parts.size == 3) Key(parts[0], parts[1], parts[2]) else null
    }

    data class Key(val collection: String, val platformKey: String, val name: String)

    private fun keyOf(rom: CatalogReader.Rom) =
        listOf(rom.collection, rom.platformKey, rom.name).joinToString(SEPARATOR)

    private companion object {
        const val STORE_KEY = "roms"
        /** ROM names contain spaces, slashes and brackets; only a control character is safe. */
        const val SEPARATOR = "\u0000"
    }
}
