package com.pawelwywiol.minervahelper.data.metadata

import android.content.Context
import com.pawelwywiol.minervahelper.data.catalog.CatalogReader
import com.pawelwywiol.minervahelper.data.placement.PlatformFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

data class GameInfo(val title: String?, val system: String?, val cover: File?) {
    val isEmpty: Boolean get() = title == null && cover == null
}

/**
 * Best-effort enrichment for the detail pane from two free, keyless sources: an anonymous hash
 * lookup at hasheous.org (canonical title and system — matches bare ROM files, rarely zips) and
 * cover art from thumbnails.libretro.com, keyed by game name, with a few name variants tried.
 *
 * Results — including "nothing found" — are cached on disk, one lookup per ROM ever. A network
 * failure returns an empty result and caches nothing, so the next visit retries. Nothing here may
 * ever surface an error: when this fails, the pane simply shows what the archive knows.
 */
class GameInfoLookup(context: Context, private val http: OkHttpClient) {

    private val dir = File(context.applicationContext.filesDir, "gameinfo")
    private val folders = PlatformFolders(context)

    suspend fun lookup(rom: CatalogReader.Rom): GameInfo = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val key = rom.md5 ?: "n${rom.name.hashCode().toUInt().toString(16)}-${rom.size}"
        val meta = File(dir, "$key.json")
        val coverFile = File(dir, "$key.png")
        if (meta.isFile) return@withContext cached(meta, coverFile)

        try {
            var title: String? = null
            var system: String? = null
            if (rom.md5 != null) {
                get("$HASHEOUS/api/v1/Lookup/ByHash/md5/${rom.md5}")?.let { body ->
                    val json = JSONObject(body)
                    title = json.string("name")
                    system = json.optJSONObject("platform")?.string("name")
                }
            }
            val cover = fetchCover(rom, title, coverFile)
            meta.writeText(
                JSONObject()
                    .put("title", title ?: JSONObject.NULL)
                    .put("system", system ?: JSONObject.NULL)
                    .toString()
            )
            GameInfo(title, system, cover)
        } catch (offline: IOException) {
            GameInfo(null, null, null)
        }
    }

    private fun cached(meta: File, coverFile: File): GameInfo {
        val json = runCatching { JSONObject(meta.readText()) }.getOrNull() ?: return GameInfo(null, null, null)
        return GameInfo(
            title = json.string("title"),
            system = json.string("system"),
            cover = coverFile.takeIf { it.isFile },
        )
    }

    private fun fetchCover(rom: CatalogReader.Rom, canonical: String?, out: File): File? {
        val systemDir = LIBRETRO_SYSTEMS[folders.folderFor(rom.collection, rom.platformKey)] ?: return null
        val base = rom.name.substringBeforeLast('.')
        val candidates = buildList {
            add(base)
            canonical?.let(::add)
            regionVariant(base)?.let(::add)
            add(base.substringBefore(" (").trim())
        }.map(::sanitize).filter { it.isNotBlank() }.distinct()

        for (candidate in candidates) {
            val url = "$THUMBNAILS/${encode(systemDir)}/Named_Boxarts/${encode(candidate)}.png"
            val hit = http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (res.code != 200) false
                else {
                    out.outputStream().use { checkNotNull(res.body).byteStream().copyTo(it) }
                    true
                }
            }
            if (hit) return out
        }
        return null
    }

    /** org.json returns the literal string "null" for JSON nulls — this does not. */
    private fun JSONObject.string(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun get(url: String): String? =
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (res.code == 200) checkNotNull(res.body).string() else null
        }

    /** "Title (1992)(Ocean)(EU)" → "Title (Europe)" — thumbnail names use No-Intro regions. */
    private fun regionVariant(name: String): String? {
        val title = name.substringBefore(" (").trim()
        if (title == name) return null
        val region = REGIONS.entries.firstOrNull { (tag, _) ->
            name.contains("($tag)", ignoreCase = true)
        }?.value ?: return null
        return "$title ($region)"
    }

    /** The characters the thumbnail server itself replaces in file names. */
    private fun sanitize(name: String): String = name.replace(Regex("[&*/:`<>?\\\\|]"), "_")

    private fun encode(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}

private const val HASHEOUS = "https://hasheous.org"
private const val THUMBNAILS = "https://thumbnails.libretro.com"

private val REGIONS = mapOf(
    "EU" to "Europe", "Europe" to "Europe",
    "US" to "USA", "USA" to "USA",
    "JP" to "Japan", "Japan" to "Japan",
    "World" to "World",
)

/** Destination folder slug → libretro thumbnail system directory. Extended on demand. */
private val LIBRETRO_SYSTEMS = mapOf(
    "arcade" to "MAME",
    "mame" to "MAME",
    "fbneo" to "FBNeo - Arcade Games",
    "amiga" to "Commodore - Amiga",
    "amiga600" to "Commodore - Amiga",
    "amiga1200" to "Commodore - Amiga",
    "amigacd32" to "Commodore - Amiga",
    "c64" to "Commodore - 64",
    "amstradcpc" to "Amstrad - CPC",
    "zxspectrum" to "Sinclair - ZX Spectrum",
    "atari2600" to "Atari - 2600",
    "atari5200" to "Atari - 5200",
    "atari7800" to "Atari - 7800",
    "atari800" to "Atari - 8-bit",
    "atarilynx" to "Atari - Lynx",
    "atarist" to "Atari - ST",
    "atarijaguar" to "Atari - Jaguar",
    "nes" to "Nintendo - Nintendo Entertainment System",
    "famicom" to "Nintendo - Nintendo Entertainment System",
    "snes" to "Nintendo - Super Nintendo Entertainment System",
    "n64" to "Nintendo - Nintendo 64",
    "gb" to "Nintendo - Game Boy",
    "gbc" to "Nintendo - Game Boy Color",
    "gba" to "Nintendo - Game Boy Advance",
    "nds" to "Nintendo - Nintendo DS",
    "n3ds" to "Nintendo - Nintendo 3DS",
    "gc" to "Nintendo - GameCube",
    "wii" to "Nintendo - Wii",
    "virtualboy" to "Nintendo - Virtual Boy",
    "gamegear" to "Sega - Game Gear",
    "mastersystem" to "Sega - Master System - Mark III",
    "megadrive" to "Sega - Mega Drive - Genesis",
    "genesis" to "Sega - Mega Drive - Genesis",
    "megacd" to "Sega - Mega-CD - Sega CD",
    "saturn" to "Sega - Saturn",
    "dreamcast" to "Sega - Dreamcast",
    "dc" to "Sega - Dreamcast",
    "psx" to "Sony - PlayStation",
    "ps2" to "Sony - PlayStation 2",
    "psp" to "Sony - PlayStation Portable",
    "pcengine" to "NEC - PC Engine - TurboGrafx 16",
    "neogeo" to "SNK - Neo Geo",
    "ngp" to "SNK - Neo Geo Pocket",
    "ngpc" to "SNK - Neo Geo Pocket Color",
    "wonderswan" to "Bandai - WonderSwan",
    "colecovision" to "Coleco - ColecoVision",
    "intellivision" to "Mattel - Intellivision",
    "vectrex" to "GCE - Vectrex",
    "3do" to "The 3DO Company - 3DO",
    "msx" to "Microsoft - MSX",
    "msx2" to "Microsoft - MSX2",
    "dos" to "DOS",
)
