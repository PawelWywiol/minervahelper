package io.github.pawelwywiol.minervahelper.data.web

import android.content.Context
import android.util.Log
import io.github.pawelwywiol.minervahelper.BuildConfig
import io.github.pawelwywiol.minervahelper.data.catalog.CatalogReader
import io.github.pawelwywiol.minervahelper.data.catalog.Systems
import io.github.pawelwywiol.minervahelper.data.placement.Destinations
import io.github.pawelwywiol.minervahelper.data.queue.DownloadQueue
import io.github.pawelwywiol.minervahelper.data.settings.Preferences
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
        val pin = runCatching { JSONObject(call.receiveText()).optString("pin") }.getOrNull()
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
        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
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
