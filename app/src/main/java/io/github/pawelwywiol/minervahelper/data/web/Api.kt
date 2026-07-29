package io.github.pawelwywiol.minervahelper.data.web

import android.util.Base64
import io.github.pawelwywiol.minervahelper.data.catalog.CatalogReader
import io.github.pawelwywiol.minervahelper.data.catalog.Favourites
import io.github.pawelwywiol.minervahelper.data.catalog.Systems
import io.github.pawelwywiol.minervahelper.data.metadata.GameInfo
import io.github.pawelwywiol.minervahelper.data.placement.Destinations
import io.github.pawelwywiol.minervahelper.data.queue.DownloadQueue
import io.github.pawelwywiol.minervahelper.data.queue.DownloadState
import io.github.pawelwywiol.minervahelper.data.settings.Preferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What the browser is told, and nothing about how. Keeping the wire format here means one file
 * answers "what does a ROM look like to the web UI" — and it stays free of HTTP, so it can be read
 * next to the JavaScript that consumes it.
 *
 * No shape defined here carries the content of a downloaded file, only its name and where it went.
 */

/**
 * Which ROMs are already spoken for. Read once per request rather than per row: the queue is small,
 * but a query for every row of a 50-row page would not be.
 */
internal class Marks(queue: DownloadQueue) {
    private val downloaded = queue.downloadedKeys()
    private val queued = queue.all()
        .mapTo(HashSet()) { DownloadQueue.downloadedKey(it.torrentPath, it.soId) }

    fun apply(rom: CatalogReader.Rom, target: JSONObject): JSONObject {
        val key = DownloadQueue.downloadedKey(rom)
        return target
            .put("queued", key in queued)
            .put("downloaded", key in downloaded)
    }
}

internal fun collectionsJson(reader: CatalogReader): JSONObject {
    val array = JSONArray()
    reader.collections().forEach {
        array.put(
            JSONObject()
                .put("name", it.name)
                .put("fileCount", it.fileCount)
                .put("platformCount", it.platformCount)
        )
    }
    return JSONObject().put("collections", array)
}

/**
 * Platform rows carry the readable label the app shows, not the archive's raw key: "Nintendo/Famicom
 * & Entertainment System" is longer than most ROM names. The same [Systems] lookup the on-device
 * list uses, so the two screens never disagree about what a platform is called.
 */
internal fun platformsJson(
    reader: CatalogReader,
    systems: Systems,
    destinations: Destinations,
    collection: String,
): JSONObject {
    val array = JSONArray()
    reader.platforms(collection).forEach {
        val slug = destinations.slugFor(collection, it.key)
        array.put(
            JSONObject()
                .put("id", it.id)
                .put("key", it.key)
                .put("label", systems.labelFor(it.key, slug))
                .put("folder", destinations.folderFor(collection, it.key))
                .put("fileCount", it.fileCount)
        )
    }
    return JSONObject()
        .put("collection", collection)
        .put("platforms", array)
}

/**
 * [more] tells the browser whether to keep asking; [cursor] is where to continue from. Paging by the
 * last row seen, not by offset — an OFFSET deep into a 753k-row platform costs 313 ms on the device,
 * while seeking to a key stays flat.
 */
internal fun romsJson(roms: List<CatalogReader.Rom>, marks: Marks, pageSize: Int): JSONObject {
    val array = JSONArray()
    roms.forEach { array.put(marks.apply(it, romJson(it))) }
    val last = roms.lastOrNull()
    return JSONObject()
        .put("roms", array)
        .put("more", roms.size >= pageSize)
        .put(
            "cursor",
            last?.let { JSONObject().put("name", it.name).put("id", it.id) } ?: JSONObject.NULL,
        )
}

/**
 * The queue as one document: groups in the order they will be fetched, each with the live figures
 * for the torrent being worked on right now, then the recent downloads. The browser polls this once
 * a second, so it is deliberately one round trip rather than five.
 *
 * [working] is WorkManager's view, not ours — the browser must show the same Pause/Resume state the
 * device does, and only WorkManager knows whether a run is waiting for Wi-Fi.
 */
internal fun queueJson(
    queue: DownloadQueue,
    systems: Systems,
    destinations: Destinations,
    working: Boolean,
    freeBytes: Long,
    recentDownloads: Int,
): JSONObject {
    val groups = JSONArray()
    var total = 0L
    var count = 0
    var failed = false
    var waiting = false
    queue.byTorrent().forEach { (torrentPath, items) ->
        val rows = JSONArray()
        items.forEach { item ->
            total += item.size
            count++
            if (item.state == DownloadState.FAILED) failed = true
            if (item.state == DownloadState.QUEUED) waiting = true
            val slug = destinations.slugFor(item.collection, item.platformKey)
            rows.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("size", item.size)
                    .put("platform", systems.labelFor(item.platformKey, slug))
                    .put("folder", destinations.folderFor(item.collection, item.platformKey))
                    .put("state", item.state.name.lowercase())
                    .put("error", item.error ?: JSONObject.NULL)
            )
        }
        val active = queue.active[torrentPath]
        groups.put(
            JSONObject()
                .put("torrentPath", torrentPath)
                .put("label", torrentPath.substringAfterLast('/'))
                .put("paused", items.all { it.state == DownloadState.PAUSED })
                .put("items", rows)
                .put(
                    "active",
                    active?.let {
                        JSONObject()
                            .put("percent", it.percent)
                            .put("rate", it.rate)
                            .put("peers", it.peers)
                    } ?: JSONObject.NULL,
                )
        )
    }

    val downloaded = queue.downloaded()
    val recent = JSONArray()
    downloaded.take(recentDownloads).forEach {
        recent.put(
            JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("size", it.size)
                .put("folder", it.folder)
        )
    }

    return JSONObject()
        .put("groups", groups)
        .put("count", count)
        .put("bytes", total)
        .put("freeBytes", freeBytes)
        .put("working", working)
        .put("waiting", waiting)
        .put("failed", failed)
        .put("rootChosen", destinations.root != null)
        .put("downloaded", recent)
        .put("downloadedTotal", downloaded.size)
}

/**
 * Everything the detail pane on the device shows, plus where the file would land.
 *
 * The cover travels inside this document as a data URI rather than from a route of its own. It is
 * the one thing here that came off the network, and inlining it keeps a true statement true: no
 * route in this package returns the contents of a file on the device.
 */
internal fun romDetailJson(
    rom: CatalogReader.Rom,
    info: GameInfo,
    marks: Marks,
    favourite: Boolean,
    folder: String,
    platform: String,
): JSONObject = marks.apply(
    rom,
    romJson(rom)
        .put("torrentPath", rom.torrentPath)
        .put("md5", rom.md5 ?: JSONObject.NULL)
        .put("sha1", rom.sha1 ?: JSONObject.NULL)
        .put("title", info.title ?: JSONObject.NULL)
        .put("system", info.system ?: JSONObject.NULL)
        .put("cover", info.cover?.let(::coverDataUri) ?: JSONObject.NULL)
        .put("favourite", favourite)
        .put("folder", folder)
        .put("platform", platform),
)

private fun coverDataUri(file: File): String? = runCatching {
    "data:image/png;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
}.getOrNull()

/**
 * Favourites are stored as archive paths, not row ids, so each one has to be looked up; a favourite
 * whose ROM is missing from the current catalogue is dropped rather than shown as a broken row.
 */
internal fun favouritesJson(
    reader: CatalogReader,
    favourites: Favourites,
    marks: Marks,
): JSONObject {
    val array = JSONArray()
    favourites.all().mapNotNull(reader::rom).forEach { array.put(marks.apply(it, romJson(it))) }
    return JSONObject().put("roms", array)
}

/**
 * The settings a browser may change. The destination root is reported but not settable: choosing it
 * needs Android's own folder picker, which cannot be driven from here.
 */
internal fun settingsJson(
    queue: DownloadQueue,
    destinations: Destinations,
    preferences: Preferences,
    minConcurrent: Int,
    maxConcurrent: Int,
): JSONObject {
    val queued = queue.all().groupBy { it.collection to it.platformKey }
    val done = queue.downloaded().groupBy { it.collection to it.platformKey }
    val platforms = JSONArray()
    (queued.keys + done.keys).sortedBy { it.second }.forEach { (collection, platformKey) ->
        platforms.put(
            JSONObject()
                .put("collection", collection)
                .put("platformKey", platformKey)
                .put("folder", destinations.folderFor(collection, platformKey))
                .put("mapped", destinations.isMapped(collection, platformKey))
                .put("queued", queued[collection to platformKey]?.size ?: 0)
                .put("downloaded", done[collection to platformKey]?.size ?: 0)
        )
    }
    return JSONObject()
        .put("root", destinations.root?.lastPathSegment ?: JSONObject.NULL)
        .put("concurrentDownloads", preferences.concurrentDownloads)
        .put("unpackArchives", preferences.unpackArchives)
        .put("minConcurrentDownloads", minConcurrent)
        .put("maxConcurrentDownloads", maxConcurrent)
        .put("platforms", platforms)
}

private fun romJson(rom: CatalogReader.Rom) = JSONObject()
    .put("id", rom.id)
    .put("name", rom.name)
    .put("size", rom.size)
    .put("collection", rom.collection)
    .put("platformKey", rom.platformKey)
