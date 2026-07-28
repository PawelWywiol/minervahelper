package io.github.pawelwywiol.minervahelper.data.catalog

import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File

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

    data class Collection(val name: String, val fileCount: Long, val platformCount: Int)

    data class Platform(val id: Long, val key: String, val fileCount: Long)

    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
    )

    /**
     * A catalog built by an older schema is unusable, not merely outdated — its tables lack columns
     * the queries below rely on. Reporting it as incomplete makes the sync rebuild it.
     */
    fun isComplete(): Boolean = runCatching {
        meta("import_stage") == "done" && meta("schema_version") == CatalogSchema.VERSION.toString()
    }.getOrDefault(false)

    /** Which upstream index this catalog was built from; null for catalogs built before R34. */
    fun sourceVersion(): String? = runCatching { meta("source_version") }.getOrNull()

    private fun meta(key: String): String? =
        db.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key))
            .use { if (it.moveToFirst()) it.getString(0) else null }

    fun collections(): List<Collection> =
        db.rawQuery(
            "SELECT collection, sum(file_count), count(*) FROM platform " +
                "GROUP BY collection HAVING sum(file_count) > 0 ORDER BY collection",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Collection(c.getString(0), c.getLong(1), c.getInt(2)))
            }
        }

    fun platforms(collection: String): List<Platform> =
        db.rawQuery(
            "SELECT id, platform_key, file_count FROM platform " +
                "WHERE collection = ? AND file_count > 0 ORDER BY platform_key",
            arrayOf(collection),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Platform(c.getLong(0), c.getString(1), c.getLong(2)))
            }
        }

    /**
     * Paged by the last row seen rather than by OFFSET: skipping 200k index entries costs 313 ms on
     * the target device, while seeking to a key is flat. [after] is the last Rom of the previous
     * page — null for the first one. Names repeat, so id breaks the tie.
     */
    fun browse(platformId: Long, after: Rom? = null, limit: Int = 50): List<Rom> =
        browse(platformId, after?.name, after?.id, limit)

    /**
     * The same page, addressed by the cursor's two fields rather than by the row itself — the
     * browser remote gets them back as query parameters and has no Rom to hand.
     */
    fun browse(platformId: Long, afterName: String?, afterId: Long?, limit: Int): List<Rom> {
        val args = mutableListOf(platformId.toString())
        val cursor = if (afterName != null && afterId != null) {
            args += listOf(afterName, afterName, afterId.toString())
            " AND (f.name > ? OR (f.name = ? AND f.id > ?))"
        } else {
            ""
        }
        return roms(
            "FROM file f",
            "f.platform_id = ?$cursor ORDER BY f.name, f.id",
            args.toTypedArray(),
            limit,
            offset = 0,
        )
    }

    /** The page that precedes [before], in list order — lets a trimmed list scroll back up. */
    fun browseBefore(platformId: Long, before: Rom, limit: Int = 50): List<Rom> = roms(
        "FROM file f",
        "f.platform_id = ? AND (f.name < ? OR (f.name = ? AND f.id < ?)) " +
            "ORDER BY f.name DESC, f.id DESC",
        arrayOf(platformId.toString(), before.name, before.name, before.id.toString()),
        limit,
        offset = 0,
    ).reversed()

    fun rom(id: Long): Rom? =
        roms("FROM file f", "f.id = ?", arrayOf(id.toString()), limit = 1, offset = 0).firstOrNull()

    fun rom(key: Favourites.Key): Rom? = roms(
        "FROM file f",
        "p.collection = ? AND p.platform_key = ? AND f.name = ?",
        arrayOf(key.collection, key.platformKey, key.name),
        limit = 1,
        offset = 0,
    ).firstOrNull()

    fun count(): Long =
        db.rawQuery("SELECT count(*) FROM file", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    fun search(query: String, limit: Int = 50, offset: Int = 0): List<Rom> {
        val match = toMatchExpression(query) ?: return emptyList()
        val hits = roms(
            "FROM file_fts j JOIN file f ON f.id = j.rowid",
            "j.name MATCH ?",
            arrayOf(match),
            limit,
            offset,
        )
        // Words in a name are not always separated — "zoom amiga" must also find "ZoomAmiga",
        // so the terms glued together run as a second prefix search.
        val joined = toJoinedMatchExpression(query) ?: return hits
        if (hits.size >= limit) return hits
        val seen = hits.mapTo(HashSet()) { it.id }
        val extra = roms(
            "FROM file_fts j JOIN file f ON f.id = j.rowid",
            "j.name MATCH ?",
            arrayOf(joined),
            limit - hits.size,
            offset = 0,
        )
        return hits + extra.filterNot { it.id in seen }
    }

    private fun roms(from: String, where: String, args: Array<String>, limit: Int, offset: Int): List<Rom> =
        db.rawQuery(
            "SELECT f.id, f.name, f.size, p.collection, p.platform_key, t.path, f.so_id, f.md5, f.sha1 " +
                "$from " +
                "JOIN platform p ON p.id = f.platform_id " +
                "JOIN torrent t ON t.id = f.torrent_id " +
                "WHERE $where LIMIT ? OFFSET ?",
            args + arrayOf(limit.toString(), offset.toString()),
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
    val terms = searchTerms(query)
    if (terms.isEmpty()) return null
    return terms.mapIndexed { index, term ->
        if (index == terms.lastIndex) "$term*" else term
    }.joinToString(" ")
}

/** "zoom amiga" → "zoomamiga*"; null when there is nothing to glue. */
private fun toJoinedMatchExpression(query: String): String? {
    val terms = searchTerms(query)
    if (terms.size < 2) return null
    return terms.joinToString("") + "*"
}

private fun searchTerms(query: String): List<String> =
    query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
