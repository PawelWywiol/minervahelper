package com.pawelwywiol.minervahelper.data.catalog

import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File

/** Read-only access to the normalized catalog. */
class CatalogReader(file: File) : Closeable {

    data class Rom(
        val id: Long,
        val name: String,
        val size: Long,
        val collection: String,
        val platformKey: String,
        val torrentPath: String,
        val soId: Int,
        val md5: String?,
        val sha1: String?,
    )

    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
    )

    fun isComplete(): Boolean = runCatching {
        db.rawQuery("SELECT value FROM meta WHERE key = 'import_stage'", null)
            .use { it.moveToFirst() && it.getString(0) == "done" }
    }.getOrDefault(false)

    fun count(): Long =
        db.rawQuery("SELECT count(*) FROM file", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    fun search(query: String, limit: Int = 50, offset: Int = 0): List<Rom> {
        val match = toMatchExpression(query) ?: return emptyList()
        return searchRaw(match, limit, offset)
    }

    private fun searchRaw(query: String, limit: Int, offset: Int): List<Rom> =
        db.rawQuery(
            "SELECT f.id, f.name, f.size, p.collection, p.platform_key, t.path, f.so_id, f.md5, f.sha1 " +
                "FROM file_fts j " +
                "JOIN file f ON f.id = j.rowid " +
                "JOIN dir d ON d.id = f.dir_id " +
                "JOIN platform p ON p.id = d.platform_id " +
                "JOIN torrent t ON t.id = f.torrent_id " +
                "WHERE j.name MATCH ? LIMIT ? OFFSET ?",
            arrayOf(query, limit.toString(), offset.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Rom(
                            id = c.getLong(0),
                            name = c.getString(1),
                            size = c.getLong(2),
                            collection = c.getString(3),
                            platformKey = c.getString(4),
                            torrentPath = c.getString(5),
                            soId = c.getInt(6),
                            md5 = c.getBlob(7)?.hex(),
                            sha1 = c.getBlob(8)?.hex(),
                        )
                    )
                }
            }
        }

    override fun close() = db.close()
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

/**
 * ROM names are full of FTS4 operator characters ("-", quotes, parentheses), so user input is
 * reduced to bare alphanumeric terms; the last one gets a prefix wildcard for search-as-you-type.
 *
 * Terms must NOT be quoted: in FTS4 `"r"*` is a phrase, not a prefix, and matches far less than
 * `r*` — quoting silently breaks incremental search. Lowercasing neutralises OR/NOT/NEAR, which
 * are only operators in upper case.
 *
 * Returns null when nothing searchable remains.
 */
private fun toMatchExpression(query: String): String? {
    val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return null
    return terms.mapIndexed { index, term ->
        if (index == terms.lastIndex) "$term*" else term
    }.joinToString(" ")
}
