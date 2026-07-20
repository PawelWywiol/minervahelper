package com.pawelwywiol.minervahelper.data.catalog

internal object CatalogSchema {

    const val VERSION = 2

    val DDL = listOf(
        "CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS torrent(id INTEGER PRIMARY KEY, path TEXT NOT NULL, btih BLOB NOT NULL)",
        "CREATE TABLE IF NOT EXISTS platform(" +
            "id INTEGER PRIMARY KEY, collection TEXT NOT NULL, platform_key TEXT NOT NULL," +
            "file_count INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS dir(id INTEGER PRIMARY KEY, path TEXT NOT NULL, platform_id INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS file(" +
            "id INTEGER PRIMARY KEY, dir_id INTEGER NOT NULL, name TEXT NOT NULL, size INTEGER NOT NULL," +
            "torrent_id INTEGER NOT NULL, so_id INTEGER NOT NULL, md5 BLOB, sha1 BLOB, crc32 INTEGER," +
            "platform_id INTEGER NOT NULL)",
    )

    val INDEXES = listOf(
        "CREATE INDEX IF NOT EXISTS ix_file_torrent ON file(torrent_id)",
        "CREATE INDEX IF NOT EXISTS ix_file_dir ON file(dir_id)",
        "CREATE INDEX IF NOT EXISTS ix_dir_platform ON dir(platform_id)",
        // Browsing a platform alphabetically. Without it SQLite sorts the whole platform for every
        // page (753k rows for MAME): measured 68 ms -> 0.03 ms per page, at the cost of ~160 MB.
        "CREATE INDEX IF NOT EXISTS ix_file_platform_name ON file(platform_id, name)",
    )

    const val CREATE_FTS =
        "CREATE VIRTUAL TABLE IF NOT EXISTS file_fts USING fts4(content=\"file\", name, tokenize=unicode61)"

    /**
     * Rows without so_id are not addressable inside their torrent: upstream `.partial` leftovers,
     * nested `.torrent` files and `.DS_Store`. 13 real ROMs (non-latin names) are lost this way —
     * recoverable by matching the name against TorrentInfo if ever needed.
     */
    const val SOURCE_FILTER = "torrents IS NOT NULL AND so_id IS NOT NULL"

    /** Platform is not stored upstream — it is derived from the leading segments of full_path. */
    fun platformOf(dirPath: String): Pair<String, String> {
        val seg = dirPath.removePrefix("./").split('/')
        val collection = seg.firstOrNull().orEmpty()
        val key = when {
            collection == "No-Intro" || collection == "Redump" -> seg.getOrElse(1) { "" }
            collection.startsWith("TOSEC") -> seg.drop(1).take(2).joinToString("/")
            collection == "MAME" || collection == "HBMAME" -> "mame"
            collection == "FBNeo" -> "fbneo"
            else -> seg.getOrElse(1) { collection }
        }
        return collection to key
    }
}
