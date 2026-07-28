package io.github.pawelwywiol.minervahelper.data.web

import android.content.Context
import android.os.StatFs
import android.util.Log
import io.github.pawelwywiol.minervahelper.BuildConfig
import io.github.pawelwywiol.minervahelper.data.catalog.CatalogReader
import io.github.pawelwywiol.minervahelper.data.catalog.Favourites
import io.github.pawelwywiol.minervahelper.data.catalog.Systems
import io.github.pawelwywiol.minervahelper.data.metadata.GameInfoLookup
import io.github.pawelwywiol.minervahelper.data.placement.Destinations
import io.github.pawelwywiol.minervahelper.data.placement.FileMover
import io.github.pawelwywiol.minervahelper.data.placement.FilePlacement
import io.github.pawelwywiol.minervahelper.data.queue.DownloadQueue
import io.github.pawelwywiol.minervahelper.data.settings.MAX_CONCURRENT_DOWNLOADS
import io.github.pawelwywiol.minervahelper.data.settings.MIN_CONCURRENT_DOWNLOADS
import io.github.pawelwywiol.minervahelper.data.settings.Preferences
import io.github.pawelwywiol.minervahelper.work.DownloadWorker
import okhttp3.OkHttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.host
import io.ktor.server.request.receiveText
import io.ktor.server.request.userAgent
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket

private const val TAG = "MinervaWeb"

/**
 * The browser remote's HTTP server, embedded in the app because the app is the only thing that has
 * the catalog, the queue and the folder grants. Nothing about it is a general-purpose server: it
 * listens on one address, answers one origin, and serves a UI that ships inside the APK.
 *
 * It never serves the content of a downloaded file. That is the project's hard rule, and the
 * absence of such a route is the whole of its enforcement — there is no flag to get it wrong.
 */
class WebServer(private val context: Context, private val pairing: Pairing) {

    private val queue by lazy { DownloadQueue.get(context) }
    private val destinations by lazy { Destinations.get(context) }
    private val preferences by lazy { Preferences.get(context) }
    private val systems by lazy { Systems.get(context) }
    private val favourites by lazy { Favourites.get(context) }
    private val gameInfo by lazy { GameInfoLookup(context, OkHttpClient()) }

    private var engine: EmbeddedServer<*, *>? = null

    /** The address the server answers on, and the only Host header it accepts. */
    private var boundHost: String = ""

    /** Returns the port it settled on. */
    fun start(host: String): Int {
        stop()
        boundHost = host
        val port = freePort(host)
        engine = embeddedServer(CIO, host = host, port = port) {
            routing {
                post("/api/pair") { pair(call) }
                post("/api/logout") { logout(call) }
                get("/api/state") { if (guard(call)) state(call) }
                get("/api/collections") { if (guard(call)) collections(call) }
                get("/api/collections/{name}/platforms") { if (guard(call)) platforms(call) }
                get("/api/platforms/{id}/roms") { if (guard(call)) roms(call) }
                get("/api/search") { if (guard(call)) search(call) }
                get("/api/roms/{id}") { if (guard(call)) rom(call) }
                get("/api/favourites") { if (guard(call)) favourites(call) }
                post("/api/favourites") { if (guard(call, mutation = true)) toggleFavourite(call) }
                get("/api/settings") { if (guard(call)) settings(call) }
                post("/api/settings") { if (guard(call, mutation = true)) updateSettings(call) }
                get("/api/queue") { if (guard(call)) queue(call) }
                post("/api/queue") { if (guard(call, mutation = true)) toggleQueue(call) }
                post("/api/queue/pause") { if (guard(call, mutation = true)) pause(call) }
                post("/api/queue/move") { if (guard(call, mutation = true)) move(call) }
                post("/api/queue/run") { if (guard(call, mutation = true)) run(call) }
                post("/api/queue/retry") { if (guard(call, mutation = true)) retry(call) }
                delete("/api/queue/{id}") { if (guard(call, mutation = true)) removeQueued(call) }
                delete("/api/downloaded/{id}") { if (guard(call, mutation = true)) forget(call) }
                get("/") { if (trusted(call)) asset(call, "index.html") }
                get("/{path...}") {
                    if (trusted(call)) {
                        asset(call, call.parameters.getAll("path")?.joinToString("/").orEmpty())
                    }
                }
            }
        }.also { it.start(wait = false) }
        return port
    }

    fun stop() {
        engine?.let { runCatching { it.stop(0, GRACE_MS) }.onFailure { e -> Log.w(TAG, "stop", e) } }
        engine = null
    }

    /**
     * Bound before Ktor gets the port so a clash is a plain "try the next one" rather than an
     * exception surfacing on a background coroutine after start() has already returned.
     */
    private fun freePort(host: String): Int {
        val address = InetAddress.getByName(host)
        for (port in DEFAULT_PORT until DEFAULT_PORT + PORT_TRIES) {
            if (runCatching { ServerSocket(port, 1, address).close() }.isSuccess) return port
        }
        error("no free port in $DEFAULT_PORT..${DEFAULT_PORT + PORT_TRIES - 1}")
    }

    /**
     * Every request has to name the address we bound to — pages and API alike. Without this a page
     * anywhere on the internet could point a hostname it controls at this device and have the
     * browser talk to it: DNS rebinding, the standard way LAN-only services get reached from
     * outside.
     *
     * Mutations additionally carry a header no cross-site form can set, which together with a
     * strictly same-site cookie is what stops another page acting on the user's behalf.
     */
    private suspend fun trusted(call: ApplicationCall, mutation: Boolean = false): Boolean {
        val wrong = call.request.host() != boundHost ||
            (mutation && call.request.headers[HEADER] != "1")
        if (wrong) call.respond(HttpStatusCode.Forbidden)
        return !wrong
    }

    /** [trusted], plus a paired browser. Pairing itself is the one route that cannot require this. */
    private suspend fun guard(call: ApplicationCall, mutation: Boolean = false): Boolean {
        if (!trusted(call, mutation)) return false
        val token = call.request.cookies[COOKIE]
        if (token == null || !pairing.isValid(token)) {
            call.respond(HttpStatusCode.Unauthorized)
            return false
        }
        return true
    }

    private suspend fun pair(call: ApplicationCall) {
        if (!trusted(call, mutation = true)) return
        val pin = body(call)?.optString("pin")
        val token = pin?.let { pairing.redeem(it, call.request.userAgent().orEmpty()) }
        // Every attempt is recorded, granted or not. Handing out access is the one thing here worth
        // being able to account for afterwards; the code and the token itself are never logged.
        Log.i(TAG, "pair attempt granted=${token != null} agent=${call.request.userAgent()}")
        if (token == null) {
            // Deliberately vague: telling a caller whether the code was wrong, stale or locked out
            // tells it how close it is.
            json(call, JSONObject().put("paired", false), HttpStatusCode.Unauthorized)
            return
        }
        // Written out rather than built with Ktor's Cookie, which appends its own "$x-enc" attribute
        // saying how it encoded the value. This header carries the security properties of the whole
        // feature, so it reads exactly as it goes out. Base64url needs no encoding.
        call.response.headers.append(
            HttpHeaders.SetCookie,
            "$COOKIE=$token; Max-Age=$COOKIE_MAX_AGE; Path=/; HttpOnly; SameSite=Strict",
        )
        json(call, JSONObject().put("paired", true))
    }

    private suspend fun logout(call: ApplicationCall) {
        if (!guard(call, mutation = true)) return
        call.request.cookies[COOKIE]?.let(pairing::revokeToken)
        json(call, JSONObject().put("ok", true))
    }

    private suspend fun state(call: ApplicationCall) {
        val romCount = openCatalog(context)?.use { it.count() }
        val active = JSONArray()
        queue.active.forEach { (_, value) ->
            active.put(
                JSONObject()
                    .put("label", value.label)
                    .put("percent", value.percent)
                    .put("rate", value.rate)
                    .put("peers", value.peers)
            )
        }
        json(
            call,
            JSONObject()
                .put("version", BuildConfig.VERSION_NAME)
                .put("catalogReady", romCount != null)
                .put("romCount", romCount ?: 0L)
                .put("rootChosen", destinations.root != null)
                .put("concurrentDownloads", preferences.concurrentDownloads)
                .put("queued", queue.all().size)
                .put("downloaded", queue.downloaded().size)
                .put("active", active),
        )
    }

    private suspend fun collections(call: ApplicationCall) =
        withCatalogOr(call) { json(call, collectionsJson(it)) }

    private suspend fun platforms(call: ApplicationCall) {
        val collection = call.parameters["name"].orEmpty()
        withCatalogOr(call) { json(call, platformsJson(it, systems, destinations, collection)) }
    }

    private suspend fun roms(call: ApplicationCall) {
        val platformId = call.parameters["id"]?.toLongOrNull()
        if (platformId == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val limit = pageSize(call)
        val afterName = call.request.queryParameters["afterName"]
        val afterId = call.request.queryParameters["afterId"]?.toLongOrNull()
        withCatalogOr(call) { reader ->
            val page = reader.browse(platformId, afterName, afterId, limit)
            json(call, romsJson(page, Marks(queue), limit))
        }
    }

    private suspend fun search(call: ApplicationCall) {
        val query = call.request.queryParameters["q"].orEmpty()
        val limit = pageSize(call)
        val offset = call.request.queryParameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        if (query.isBlank()) {
            json(call, romsJson(emptyList(), Marks(queue), limit))
            return
        }
        withCatalogOr(call) { reader ->
            // Search pages by offset, not by cursor: FTS returns rows in relevance order, and there
            // is no key to seek to. The pages are shallow — nobody scrolls to hit 500 of a search.
            json(call, romsJson(reader.search(query, limit, offset), Marks(queue), limit))
        }
    }

    private suspend fun rom(call: ApplicationCall) = withRom(call) { rom ->
        val slug = destinations.slugFor(rom.collection, rom.platformKey)
        json(
            call,
            romDetailJson(
                rom = rom,
                info = gameInfo.lookup(rom),
                marks = Marks(queue),
                favourite = favourites.contains(rom),
                folder = destinations.folderFor(rom.collection, rom.platformKey),
                platform = systems.labelFor(rom.platformKey, slug),
            ),
        )
    }

    private suspend fun favourites(call: ApplicationCall) =
        withCatalogOr(call) { json(call, favouritesJson(it, favourites, Marks(queue))) }

    private suspend fun toggleFavourite(call: ApplicationCall) = withRom(call) { rom ->
        favourites.toggle(rom)
        json(call, JSONObject().put("favourite", favourites.contains(rom)))
    }

    private suspend fun settings(call: ApplicationCall) = json(
        call,
        settingsJson(queue, destinations, preferences, MIN_CONCURRENT_DOWNLOADS, MAX_CONCURRENT_DOWNLOADS),
    )

    /**
     * A folder change offers the same choice the device screen does: files already downloaded either
     * follow the mapping or stay where they are. The browser answers up front, because a page that
     * has to ask afterwards would have to hold half-applied state.
     */
    private suspend fun updateSettings(call: ApplicationCall) {
        val request = body(call) ?: return call.respond(HttpStatusCode.BadRequest)
        request.optInt("concurrentDownloads", 0).takeIf { it > 0 }
            ?.let(preferences::updateConcurrentDownloads)

        val collection = request.optString("collection")
        val platformKey = request.optString("platformKey")
        val folder = request.optString("folder").trim()
        if (collection.isNotEmpty() && platformKey.isNotEmpty() && folder.isNotEmpty()) {
            val old = destinations.folderFor(collection, platformKey)
            destinations.setFolder(collection, platformKey, folder)
            val root = destinations.root
            if (request.optBoolean("moveFiles") && folder != old && root != null) {
                val affected = queue.downloaded().filter {
                    it.collection == collection && it.platformKey == platformKey &&
                        it.folder.ifBlank { old } == old
                }
                if (affected.isNotEmpty()) {
                    FileMover.moveBetweenFolders(FilePlacement(context), queue, root, old, folder, affected)
                }
            }
        }
        settings(call)
    }

    /** One ROM by id, whether it came from the path or from the body of a mutation. */
    private suspend fun withRom(call: ApplicationCall, block: suspend (CatalogReader.Rom) -> Unit) {
        val id = call.parameters["id"]?.toLongOrNull() ?: body(call)?.optLong("romId", -1) ?: -1
        withCatalogOr(call) { reader ->
            val rom = reader.rom(id)
            if (rom == null) call.respond(HttpStatusCode.NotFound) else block(rom)
        }
    }

    private suspend fun queue(call: ApplicationCall) = json(
        call,
        queueJson(
            queue = queue,
            systems = systems,
            destinations = destinations,
            working = DownloadWorker.isActive(context),
            freeBytes = StatFs(context.filesDir.path).availableBytes,
            recentDownloads = RECENT_DOWNLOADS,
        ),
    )

    /**
     * Queueing goes through the same call the on-device button makes, so a file already present on
     * the card is recorded as downloaded here too instead of being fetched twice. The answer is the
     * ROM's marks as they now stand, which is what the browse list repaints from.
     */
    private suspend fun toggleQueue(call: ApplicationCall) = withRom(call) { rom ->
        DownloadWorker.toggleDownload(context, rom)
        json(
            call,
            JSONObject()
                .put("queued", queue.contains(rom))
                .put("downloaded", queue.isDownloaded(rom)),
        )
    }

    /** One item by id, or a whole torrent by path — the device screen offers both. */
    private suspend fun pause(call: ApplicationCall) {
        val request = body(call) ?: return call.respond(HttpStatusCode.BadRequest)
        val paused = request.optBoolean("paused")
        val torrentPath = request.optString("torrentPath").takeIf { it.isNotEmpty() }
        val id = request.optLong("id", -1)
        when {
            torrentPath != null -> queue.setGroupPaused(torrentPath, paused)
            id >= 0 -> queue.setPaused(id, paused)
            else -> return call.respond(HttpStatusCode.BadRequest)
        }
        // Resuming has to wake the worker: it only ever picks up rows that are QUEUED, and it may
        // well have finished the run that skipped them.
        if (!paused) DownloadWorker.enqueue(context)
        json(call, JSONObject().put("ok", true))
    }

    private suspend fun move(call: ApplicationCall) {
        val request = body(call) ?: return call.respond(HttpStatusCode.BadRequest)
        queue.moveGroup(request.optInt("from", -1), request.optInt("to", -1))
        json(call, JSONObject().put("ok", true))
    }

    private suspend fun run(call: ApplicationCall) {
        val request = body(call) ?: return call.respond(HttpStatusCode.BadRequest)
        if (request.optBoolean("running")) DownloadWorker.enqueue(context)
        else DownloadWorker.cancel(context)
        json(call, JSONObject().put("ok", true))
    }

    private suspend fun retry(call: ApplicationCall) {
        queue.retryFailed()
        DownloadWorker.enqueue(context)
        json(call, JSONObject().put("ok", true))
    }

    private suspend fun removeQueued(call: ApplicationCall) = byId(call) { queue.remove(it) }

    /** Forgets the record only; the file itself is never touched from here — or from anywhere. */
    private suspend fun forget(call: ApplicationCall) = byId(call) { queue.removeDownloaded(it) }

    private suspend fun byId(call: ApplicationCall, block: (Long) -> Unit) {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        block(id)
        json(call, JSONObject().put("ok", true))
    }

    private suspend fun body(call: ApplicationCall): JSONObject? =
        runCatching { JSONObject(call.receiveText()) }.getOrNull()

    private fun pageSize(call: ApplicationCall): Int =
        call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_PAGE) ?: DEFAULT_PAGE

    /**
     * Every catalogue route needs the same answer when there is no catalogue yet: say so plainly, so
     * the browser can send the owner to the device to import it instead of showing an empty list.
     */
    private suspend fun withCatalogOr(call: ApplicationCall, block: suspend (CatalogReader) -> Unit) {
        val reader = openCatalog(context)
        if (reader == null) {
            json(call, JSONObject().put("catalogReady", false), HttpStatusCode.ServiceUnavailable)
            return
        }
        reader.use { block(it) }
    }

    private suspend fun json(
        call: ApplicationCall,
        body: JSONObject,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.respondText(body.toString(), ContentType.Application.Json, status)
    }

    private suspend fun asset(call: ApplicationCall, path: String) {
        val name = path.trimStart('/').ifEmpty { "index.html" }
        // Assets are read by name straight from the APK, so a traversal attempt must not even be
        // opened; there is nothing below assets/web that belongs to a browser.
        if (name.contains("..") || name.startsWith("/")) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val bytes = runCatching {
            context.assets.open("web/$name").use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        // no-store, not no-cache: these files ship inside the APK, change with every update and
        // carry no ETag for a browser to revalidate against, so a stale copy would have nothing to
        // correct it. The whole UI is ~35 kB over a LAN — cheaper than a wrong UI.
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("Referrer-Policy", "no-referrer")
        if (name.endsWith(".html")) call.response.headers.append("Content-Security-Policy", CSP)
        call.respondBytes(bytes, contentTypeOf(name))
    }

    private fun contentTypeOf(name: String): ContentType = when (name.substringAfterLast('.')) {
        "html" -> ContentType.Text.Html
        "css" -> ContentType.Text.CSS
        "js" -> ContentType.Application.JavaScript
        "json" -> ContentType.Application.Json
        "svg" -> ContentType.Image.SVG
        "png" -> ContentType.Image.PNG
        else -> ContentType.Application.OctetStream
    }

    companion object {
        const val DEFAULT_PORT = 8080
        const val COOKIE = "mh"
        const val HEADER = "X-Minerva"

        private const val PORT_TRIES = 10
        private const val GRACE_MS = 500L

        /** Matches the on-device pager's page, so both screens make the same number of queries. */
        private const val DEFAULT_PAGE = 50
        private const val MAX_PAGE = 200

        /** As many finished downloads as the device screen lists; the rest show as ✓ in the lists. */
        private const val RECENT_DOWNLOADS = 20
        private const val COOKIE_MAX_AGE = 365 * 24 * 60 * 60

        /**
         * Cover art is the one thing loaded from outside, and it goes straight from libretro to the
         * browser rather than through the device.
         */
        private const val CSP = "default-src 'self'; " +
            "img-src 'self' data: https://thumbnails.libretro.com; " +
            "script-src 'self'; style-src 'self'; connect-src 'self'; " +
            "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
    }
}
