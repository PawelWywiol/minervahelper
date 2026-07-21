package io.github.pawelwywiol.minervahelper.data.catalog

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Normalizes upstream hashes.db into catalog.db in a single streaming pass:
 * torrent path / directory / platform are deduplicated into lookup tables and
 * hex hashes become BLOBs, which turns 1.64 GB into ~395 MB.
 *
 * Runs as one clean build; see the note in import() for why it does not resume.
 */
class CatalogImporter {

    data class Progress(val imported: Long, val total: Long)

    suspend fun import(
        source: File,
        target: File,
        sourceVersion: String?,
        onProgress: suspend (Progress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // Always a clean build. Journalling is off for speed, so an interrupted import can leave a
        // corrupt file — resuming into it would produce a silently wrong catalog. At ~60 s a restart
        // is cheaper than making that safe, and this also covers re-importing an updated hashes.db.
        target.delete()

        val db = SQLiteDatabase.openOrCreateDatabase(target, null)
        try {
            db.rawQuery("PRAGMA journal_mode=OFF", null).use { it.moveToFirst() }
            db.execSQL("PRAGMA synchronous=OFF")
            CatalogSchema.DDL.forEach(db::execSQL)
            db.execSQL("ATTACH DATABASE ? AS src", arrayOf(source.absolutePath))

            val total = db.longQuery("SELECT count(*) FROM src.files WHERE ${CatalogSchema.SOURCE_FILTER}")
            val state = ImportState()
            val insert = Inserts(db)
            var lastId = 0L
            var imported = 0L

            while (true) {
                coroutineContext.ensureActive()
                db.beginTransaction()
                val batch = try {
                    val n = importBatch(db, lastId, insert, state) { lastId = it }
                    db.setTransactionSuccessful()
                    n
                } finally {
                    db.endTransaction()
                }
                if (batch == 0) break
                imported += batch
                onProgress(Progress(imported, total))
            }

            db.writeFileCounts(state.platformCounts)
            CatalogSchema.INDEXES.forEach(db::execSQL)
            db.execSQL(CatalogSchema.CREATE_FTS)
            db.execSQL("INSERT INTO file_fts(rowid, name) SELECT id, name FROM file")
            db.execSQL("INSERT INTO file_fts(file_fts) VALUES('optimize')")
            db.setMeta(KEY_VERSION, CatalogSchema.VERSION.toString())
            // Which upstream file this was built from, so a later check can tell whether the
            // archive has moved on (R34).
            sourceVersion?.let { db.setMeta(KEY_SOURCE, it) }
            db.setMeta(KEY_STAGE, STAGE_DONE)
        } finally {
            runCatching { db.execSQL("DETACH DATABASE src") }
            db.close()
        }
    }

    private fun importBatch(
        db: SQLiteDatabase,
        fromId: Long,
        insert: Inserts,
        state: ImportState,
        onId: (Long) -> Unit,
    ): Int {
        val (torrents, dirs, platforms, dirPlatform, platformCounts) = state
        val cursor = db.rawQuery(
            "SELECT id, full_path, file_name, size, md5, sha1, crc32, torrents, so_id, magnet " +
                "FROM src.files WHERE ${CatalogSchema.SOURCE_FILTER} AND id > ? ORDER BY id LIMIT $BATCH",
            arrayOf(fromId.toString()),
        )
        var count = 0
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val fullPath = it.getString(1)
                val name = it.getString(2)
                val torrentPath = it.getString(7)

                val torrentId = torrents.getOrPut(torrentPath) {
                    insert.torrent.bindLong(1, torrents.size + 1L)
                    insert.torrent.bindString(2, torrentPath)
                    insert.torrent.bindBlob(3, it.getString(9).btih())
                    insert.torrent.executeInsert()
                    torrents.size + 1L
                }
                val dirPath = fullPath.dropLast(name.length + 1)
                val dirId = dirs.getOrPut(dirPath) {
                    val (collection, key) = CatalogSchema.platformOf(dirPath)
                    val platformId = platforms.getOrPut("$collection\u0000$key") {
                        insert.platform.bindLong(1, platforms.size + 1L)
                        insert.platform.bindString(2, collection)
                        insert.platform.bindString(3, key)
                        insert.platform.executeInsert()
                        platforms.size + 1L
                    }
                    insert.dir.bindLong(1, dirs.size + 1L)
                    insert.dir.bindString(2, dirPath)
                    insert.dir.bindLong(3, platformId)
                    insert.dir.executeInsert()
                    dirPlatform[dirs.size + 1L] = platformId
                    dirs.size + 1L
                }
                val platformId = dirPlatform.getValue(dirId)
                platformCounts[platformId] = (platformCounts[platformId] ?: 0L) + 1L

                with(insert.file) {
                    bindLong(1, dirId)
                    bindString(2, name)
                    bindLong(3, it.getLong(3))
                    bindLong(4, torrentId)
                    bindLong(5, it.getLong(8))
                    bindHex(6, it.getString(4))
                    bindHex(7, it.getString(5))
                    it.getString(6)?.toLongOrNull(16)?.let { crc -> bindLong(8, crc) } ?: bindNull(8)
                    bindLong(9, platformId)
                    executeInsert()
                }
                count++
                onId(id)
            }
        }
        return count
    }

    private data class ImportState(
        val torrents: MutableMap<String, Long> = HashMap(),
        val dirs: MutableMap<String, Long> = HashMap(),
        val platforms: MutableMap<String, Long> = HashMap(),
        val dirPlatform: MutableMap<Long, Long> = HashMap(),
        val platformCounts: MutableMap<Long, Long> = HashMap(),
    )

    private class Inserts(db: SQLiteDatabase) {
        val torrent: SQLiteStatement = db.compileStatement("INSERT INTO torrent VALUES(?,?,?)")
        val platform: SQLiteStatement =
            db.compileStatement("INSERT INTO platform(id,collection,platform_key) VALUES(?,?,?)")
        val dir: SQLiteStatement = db.compileStatement("INSERT INTO dir VALUES(?,?,?)")
        val file: SQLiteStatement = db.compileStatement(
            "INSERT INTO file(dir_id,name,size,torrent_id,so_id,md5,sha1,crc32,platform_id) " +
                "VALUES(?,?,?,?,?,?,?,?,?)"
        )
    }

    private companion object {
        /** Kept small enough that a batch fits one 2 MB CursorWindow — a larger cursor
         * refills by re-running the query and skipping rows, which is quadratic. */
        const val BATCH = 10_000
        const val KEY_STAGE = "import_stage"
        const val KEY_VERSION = "schema_version"
        const val KEY_SOURCE = "source_version"
        const val STAGE_DONE = "done"
    }
}

private fun SQLiteStatement.bindHex(index: Int, hex: String?) {
    val bytes = hex?.hexToBytes()
    if (bytes == null) bindNull(index) else bindBlob(index, bytes)
}

private fun String.btih(): ByteArray =
    substringAfter("btih:").take(40).hexToBytes() ?: error("no btih in magnet: $this")

private fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0) return null
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val hi = Character.digit(this[i * 2], 16)
        val lo = Character.digit(this[i * 2 + 1], 16)
        if (hi < 0 || lo < 0) return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

private fun SQLiteDatabase.longQuery(sql: String): Long =
    rawQuery(sql, null).use { if (it.moveToFirst()) it.getLong(0) else 0L }

private fun SQLiteDatabase.writeFileCounts(counts: Map<Long, Long>) {
    val update = compileStatement("UPDATE platform SET file_count = ? WHERE id = ?")
    beginTransaction()
    try {
        counts.forEach { (platformId, count) ->
            update.bindLong(1, count)
            update.bindLong(2, platformId)
            update.executeUpdateDelete()
        }
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}

private fun SQLiteDatabase.setMeta(key: String, value: String) =
    execSQL("INSERT OR REPLACE INTO meta VALUES(?,?)", arrayOf(key, value))
