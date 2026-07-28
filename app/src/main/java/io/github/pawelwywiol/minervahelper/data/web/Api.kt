package io.github.pawelwywiol.minervahelper.data.web

import io.github.pawelwywiol.minervahelper.data.catalog.CatalogReader
import io.github.pawelwywiol.minervahelper.data.catalog.Systems
import io.github.pawelwywiol.minervahelper.data.placement.Destinations
import io.github.pawelwywiol.minervahelper.data.queue.DownloadQueue
import org.json.JSONArray
import org.json.JSONObject

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

private fun romJson(rom: CatalogReader.Rom) = JSONObject()
    .put("id", rom.id)
    .put("name", rom.name)
    .put("size", rom.size)
    .put("collection", rom.collection)
    .put("platformKey", rom.platformKey)
