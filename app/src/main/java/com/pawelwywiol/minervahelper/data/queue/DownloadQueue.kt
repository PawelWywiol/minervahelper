package com.pawelwywiol.minervahelper.data.queue

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.pawelwywiol.minervahelper.data.catalog.CatalogReader

enum class DownloadState { QUEUED, DOWNLOADING, VERIFYING, DONE, FAILED }

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
)

/**
 * The queue doubles as the selection basket: a ROM the user picked and a ROM waiting to download are
 * the same thing, and keeping two lists in step would only invite them to disagree.
 *
 * Stored in its own database rather than in catalog.db, which is deleted and rebuilt on re-import.
 */
class DownloadQueue(context: Context) {

    private val helper = object : SQLiteOpenHelper(context, "queue.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE item(" +
                    "id INTEGER PRIMARY KEY, name TEXT NOT NULL, size INTEGER NOT NULL," +
                    "collection TEXT NOT NULL, platform_key TEXT NOT NULL, torrent_path TEXT NOT NULL," +
                    "so_id INTEGER NOT NULL, md5 TEXT, sha1 TEXT, state TEXT NOT NULL," +
                    "added_at INTEGER NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX ux_item ON item(torrent_path, so_id)")
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            db.execSQL("DROP TABLE IF EXISTS item")
            onCreate(db)
        }
    }

    /** Bumped on every write so Compose screens reload; the table is far too small to observe. */
    var revision by mutableIntStateOf(0)
        private set

    /**
     * What the worker is doing right now. Held in memory rather than in the table: it changes every
     * second and is meaningless once the worker stops.
     */
    var active by mutableStateOf<Active?>(null)

    data class Active(val label: String, val percent: Int, val rate: Int, val peers: Int)

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

    fun toggle(rom: CatalogReader.Rom) {
        val db = helper.writableDatabase
        val removed = db.delete(
            "item", "torrent_path = ? AND so_id = ?",
            arrayOf(rom.torrentPath, rom.soId.toString()),
        )
        if (removed == 0) db.insert("item", null, rom.toValues())
        revision++
    }

    fun remove(id: Long) {
        helper.writableDatabase.delete("item", "id = ?", arrayOf(id.toString()))
        revision++
    }

    fun setState(id: Long, state: DownloadState) {
        helper.writableDatabase.execSQL(
            "UPDATE item SET state = ? WHERE id = ?", arrayOf<Any>(state.name, id),
        )
        revision++
    }

    /**
     * Grouped by torrent because that is how they will be fetched: one torrent is opened once and
     * every wanted file inside it is selected together, instead of once per ROM.
     */
    fun byTorrent(): Map<String, List<QueueItem>> = all().groupBy { it.torrentPath }

    fun all(): List<QueueItem> =
        helper.readableDatabase.rawQuery(
            "SELECT id, name, size, collection, platform_key, torrent_path, so_id, md5, sha1, state " +
                "FROM item ORDER BY added_at, id",
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
    }

    private fun CatalogReader.Rom.toValues() = ContentValues().apply {
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
    }
}
