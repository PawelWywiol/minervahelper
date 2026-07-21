package io.github.pawelwywiol.minervahelper.data.queue

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import io.github.pawelwywiol.minervahelper.data.catalog.CatalogReader

enum class DownloadState { QUEUED, DOWNLOADING, VERIFYING, DONE, FAILED, PAUSED }

data class QueueItem(
    val id: Long,
    val name: String,
    val size: Long,
    val collection: String,
    val platformKey: String,
    val torrentPath: String,
    val soId: Int,
    val md5: String?,
    val sha1: String?,
    val state: DownloadState,
    val error: String?,
)

data class DownloadedItem(
    val id: Long,
    val name: String,
    val size: Long,
    val collection: String,
    val platformKey: String,
    val folder: String,
)

/**
 * The queue doubles as the selection basket: a ROM the user picked and a ROM waiting to download are
 * the same thing, and keeping two lists in step would only invite them to disagree.
 *
 * Stored in its own database rather than in catalog.db, which is deleted and rebuilt on re-import.
 */
class DownloadQueue(context: Context) {

    private val helper = object : SQLiteOpenHelper(context, "queue.db", null, 4) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE item(" +
                    "id INTEGER PRIMARY KEY, name TEXT NOT NULL, size INTEGER NOT NULL," +
                    "collection TEXT NOT NULL, platform_key TEXT NOT NULL, torrent_path TEXT NOT NULL," +
                    "so_id INTEGER NOT NULL, md5 TEXT, sha1 TEXT, state TEXT NOT NULL," +
                    "error TEXT, added_at INTEGER NOT NULL, position INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE UNIQUE INDEX ux_item ON item(torrent_path, so_id)")
            db.execSQL(DOWNLOADED_TABLE)
            db.execSQL(DOWNLOADED_INDEX)
        }

        // Migrated rather than recreated: the table holds what the user picked, and dropping it
        // would silently empty their queue on an app update.
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            if (old < 2) db.execSQL("ALTER TABLE item ADD COLUMN error TEXT")
            if (old < 3) {
                db.execSQL(DOWNLOADED_TABLE)
                db.execSQL(DOWNLOADED_INDEX)
                // Finished items used to linger in the queue; they belong in the downloaded record.
                // Their folder was never stored, so it stays blank for these.
                db.execSQL(
                    "INSERT INTO downloaded(name, size, collection, platform_key, torrent_path, so_id, folder, completed_at) " +
                        "SELECT name, size, collection, platform_key, torrent_path, so_id, '', added_at " +
                        "FROM item WHERE state = '${DownloadState.DONE.name}'"
                )
                db.execSQL("DELETE FROM item WHERE state = '${DownloadState.DONE.name}'")
            }
            if (old < 4) {
                db.execSQL("ALTER TABLE item ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                backfillPositions(db)
            }
        }
    }

    /** Bumped on every write so Compose screens reload; the table is far too small to observe. */
    var revision by mutableIntStateOf(0)
        private set

    /**
     * What the worker is doing right now, one entry per torrent being fetched. Held in memory
     * rather than in the table: it changes every second and is meaningless once the worker stops.
     */
    var active by mutableStateOf<Map<String, Active>>(emptyMap())
        private set

    data class Active(val label: String, val percent: Int, val rate: Int, val peers: Int)

    /** Several download coroutines report at once, so the map is only ever replaced under a lock. */
    @Synchronized
    fun setActive(torrentPath: String, value: Active?) {
        active = if (value == null) active - torrentPath else active + (torrentPath to value)
    }

    @Synchronized
    fun clearActive() {
        active = emptyMap()
    }

    /**
     * A worker killed mid-download leaves rows claiming to be downloading. Nothing is downloading
     * after a restart, so they go back to the queue and are picked up again.
     */
    fun resetInterrupted() {
        helper.writableDatabase.execSQL(
            "UPDATE item SET state = ? WHERE state IN (?, ?)",
            arrayOf(DownloadState.QUEUED.name, DownloadState.DOWNLOADING.name, DownloadState.VERIFYING.name),
        )
        revision++
    }

    fun contains(rom: CatalogReader.Rom): Boolean =
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM item WHERE torrent_path = ? AND so_id = ?",
            arrayOf(rom.torrentPath, rom.soId.toString()),
        ).use { it.moveToFirst() }

    /** Returns true when the ROM was added — the caller starts the download then (R36). */
    fun toggle(rom: CatalogReader.Rom): Boolean {
        val db = helper.writableDatabase
        val removed = db.delete(
            "item", "torrent_path = ? AND so_id = ?",
            arrayOf(rom.torrentPath, rom.soId.toString()),
        )
        if (removed == 0) db.insert("item", null, rom.toValues(positionFor(rom.torrentPath)))
        revision++
        return removed == 0
    }

    /**
     * A ROM joins the position its torrent already holds; a torrent new to the queue goes last.
     * Otherwise adding a second file from a torrent already at the front would send it to the back.
     */
    private fun positionFor(torrentPath: String): Int =
        helper.readableDatabase.rawQuery(
            "SELECT MIN(position) FROM item WHERE torrent_path = ?",
            arrayOf(torrentPath),
        ).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else nextPosition()
        }

    private fun nextPosition(): Int =
        helper.readableDatabase.rawQuery("SELECT MAX(position) FROM item", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) + 1 else 0
        }

    /** A finished download leaves the queue and becomes a downloaded record (R37). */
    fun markDownloaded(item: QueueItem, folder: String) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                "downloaded", null,
                downloadedValues(item.name, item.size, item.collection, item.platformKey, item.torrentPath, item.soId, folder),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.delete("item", "id = ?", arrayOf(item.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        revision++
    }

    /** A file already sitting in the destination counts as downloaded, without queueing (R38). */
    fun recordDownloaded(rom: CatalogReader.Rom, folder: String) {
        helper.writableDatabase.insertWithOnConflict(
            "downloaded", null,
            downloadedValues(rom.name, rom.size, rom.collection, rom.platformKey, rom.torrentPath, rom.soId, folder),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        revision++
    }

    fun isDownloaded(rom: CatalogReader.Rom): Boolean =
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM downloaded WHERE torrent_path = ? AND so_id = ?",
            arrayOf(rom.torrentPath, rom.soId.toString()),
        ).use { it.moveToFirst() }

    /** For marking browse lists; keys built with [downloadedKey]. */
    fun downloadedKeys(): Set<String> =
        helper.readableDatabase.rawQuery("SELECT torrent_path, so_id FROM downloaded", null).use { c ->
            buildSet { while (c.moveToNext()) add(downloadedKey(c.getString(0), c.getInt(1))) }
        }

    fun downloaded(): List<DownloadedItem> =
        helper.readableDatabase.rawQuery(
            "SELECT id, name, size, collection, platform_key, folder FROM downloaded ORDER BY completed_at DESC, id DESC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DownloadedItem(
                            id = c.getLong(0),
                            name = c.getString(1),
                            size = c.getLong(2),
                            collection = c.getString(3),
                            platformKey = c.getString(4),
                            folder = c.getString(5),
                        )
                    )
                }
            }
        }

    /** Forgets a downloaded record; the file itself is untouched. Lists lose their ✓ for it. */
    fun removeDownloaded(id: Long) {
        helper.writableDatabase.delete("downloaded", "id = ?", arrayOf(id.toString()))
        revision++
    }

    fun setDownloadedFolder(id: Long, folder: String) {
        helper.writableDatabase.execSQL(
            "UPDATE downloaded SET folder = ? WHERE id = ?",
            arrayOf<Any>(folder, id),
        )
        revision++
    }

    private fun downloadedValues(
        name: String,
        size: Long,
        collection: String,
        platformKey: String,
        torrentPath: String,
        soId: Int,
        folder: String,
    ) = ContentValues().apply {
        put("name", name)
        put("size", size)
        put("collection", collection)
        put("platform_key", platformKey)
        put("torrent_path", torrentPath)
        put("so_id", soId)
        put("folder", folder)
        put("completed_at", System.currentTimeMillis())
    }

    fun remove(id: Long) {
        helper.writableDatabase.delete("item", "id = ?", arrayOf(id.toString()))
        revision++
    }

    fun setState(id: Long, state: DownloadState, error: String? = null) {
        helper.writableDatabase.execSQL(
            "UPDATE item SET state = ?, error = ? WHERE id = ?",
            arrayOf<Any?>(state.name, error, id),
        )
        revision++
    }

    /**
     * A paused item is skipped by the worker, which only ever picks up QUEUED rows. Pausing one
     * that is downloading right now takes effect within a second: the engine re-reads the wanted
     * set on every progress tick and deprioritises the file inside the live torrent.
     */
    fun setPaused(id: Long, paused: Boolean) {
        helper.writableDatabase.execSQL(
            "UPDATE item SET state = ?, error = NULL WHERE id = ? AND state IN (?, ?, ?)",
            arrayOf<Any>(
                if (paused) DownloadState.PAUSED.name else DownloadState.QUEUED.name, id,
                DownloadState.QUEUED.name, DownloadState.PAUSED.name, DownloadState.DOWNLOADING.name,
            ),
        )
        revision++
    }

    /** Polled once a second by a running download, so it reads the ids alone rather than every row. */
    fun pausedIds(): Set<Long> =
        helper.readableDatabase.rawQuery(
            "SELECT id FROM item WHERE state = ?",
            arrayOf(DownloadState.PAUSED.name),
        ).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    fun setGroupPaused(torrentPath: String, paused: Boolean) {
        helper.writableDatabase.execSQL(
            "UPDATE item SET state = ?, error = NULL WHERE torrent_path = ? AND state IN (?, ?, ?)",
            arrayOf<Any>(
                if (paused) DownloadState.PAUSED.name else DownloadState.QUEUED.name, torrentPath,
                DownloadState.QUEUED.name, DownloadState.PAUSED.name, DownloadState.DOWNLOADING.name,
            ),
        )
        revision++
    }

    /**
     * Reorders whole torrents, not single ROMs: one torrent is opened once and all its wanted
     * files are fetched together, so a ROM cannot overtake its own siblings.
     */
    fun moveGroup(from: Int, to: Int) {
        val order = groupOrder().toMutableList()
        if (from !in order.indices || to !in order.indices || from == to) return
        order.add(to, order.removeAt(from))
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            order.forEachIndexed { index, torrentPath ->
                db.execSQL(
                    "UPDATE item SET position = ? WHERE torrent_path = ?",
                    arrayOf<Any>(index, torrentPath),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        revision++
    }

    private fun groupOrder(): List<String> =
        helper.readableDatabase.rawQuery(
            "SELECT torrent_path FROM item GROUP BY torrent_path " +
                "ORDER BY MIN(position), MIN(added_at), MIN(id)",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    /** Puts every failed item back in the queue, clearing the reason it failed with. */
    fun retryFailed() {
        helper.writableDatabase.execSQL(
            "UPDATE item SET state = ?, error = NULL WHERE state = ?",
            arrayOf(DownloadState.QUEUED.name, DownloadState.FAILED.name),
        )
        revision++
    }

    /**
     * Grouped by torrent because that is how they will be fetched: one torrent is opened once and
     * every wanted file inside it is selected together, instead of once per ROM.
     *
     * The map keeps [all]'s order, so the first entry is the group the user put first.
     */
    fun byTorrent(): Map<String, List<QueueItem>> = all().groupBy { it.torrentPath }

    fun all(): List<QueueItem> =
        helper.readableDatabase.rawQuery(
            "SELECT id, name, size, collection, platform_key, torrent_path, so_id, md5, sha1, state, error " +
                "FROM item ORDER BY position, added_at, id",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        QueueItem(
                            id = c.getLong(0),
                            name = c.getString(1),
                            size = c.getLong(2),
                            collection = c.getString(3),
                            platformKey = c.getString(4),
                            torrentPath = c.getString(5),
                            soId = c.getInt(6),
                            md5 = c.getString(7),
                            sha1 = c.getString(8),
                            state = DownloadState.valueOf(c.getString(9)),
                            error = c.getString(10),
                        )
                    )
                }
            }
        }

    companion object {
        /**
         * The UI and the download worker must see the same queue — two instances would each keep
         * their own change counter and the screen would silently stop reflecting the download.
         */
        @Volatile
        private var instance: DownloadQueue? = null

        fun get(context: Context): DownloadQueue =
            instance ?: synchronized(this) {
                instance ?: DownloadQueue(context.applicationContext).also { instance = it }
            }

        fun downloadedKey(torrentPath: String, soId: Int) = "$torrentPath\u0000$soId"

        fun downloadedKey(rom: CatalogReader.Rom) = downloadedKey(rom.torrentPath, rom.soId)

        private const val DOWNLOADED_TABLE =
            "CREATE TABLE downloaded(" +
                "id INTEGER PRIMARY KEY, name TEXT NOT NULL, size INTEGER NOT NULL," +
                "collection TEXT NOT NULL, platform_key TEXT NOT NULL, torrent_path TEXT NOT NULL," +
                "so_id INTEGER NOT NULL, folder TEXT NOT NULL, completed_at INTEGER NOT NULL)"
        private const val DOWNLOADED_INDEX =
            "CREATE UNIQUE INDEX ux_downloaded ON downloaded(torrent_path, so_id)"
    }

    private fun CatalogReader.Rom.toValues(position: Int) = ContentValues().apply {
        put("name", name)
        put("size", size)
        put("collection", collection)
        put("platform_key", platformKey)
        put("torrent_path", torrentPath)
        put("so_id", soId)
        put("md5", md5)
        put("sha1", sha1)
        put("state", DownloadState.QUEUED.name)
        put("added_at", System.currentTimeMillis())
        put("position", position)
    }
}

/**
 * Existing queues have no order beyond when things were added, so the first ordering must
 * reproduce exactly what the user already saw: torrents ranked by their oldest item.
 */
private fun backfillPositions(db: SQLiteDatabase) {
    val paths = db.rawQuery(
        "SELECT torrent_path FROM item GROUP BY torrent_path ORDER BY MIN(added_at), MIN(id)",
        null,
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    paths.forEachIndexed { index, path ->
        db.execSQL("UPDATE item SET position = ? WHERE torrent_path = ?", arrayOf<Any>(index, path))
    }
}
