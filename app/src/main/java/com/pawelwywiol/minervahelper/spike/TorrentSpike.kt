package com.pawelwywiol.minervahelper.spike

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * M0 spike: verifies the full risky path on the target device —
 * native lib loads, .torrent fetched over HTTPS, selective download by file index, hash matches.
 */
object TorrentSpike {

    private const val ASSETS_BASE = "https://minerva-archive.org/assets/"

    /** Small torrent (17 files) used as the smoke test. */
    private const val TORRENT_PATH = "Minerva_Myrient_v0.3/Minerva_Myrient - TOSEC - Panic.torrent"

    /** so_id 0 -> "SynkDate (2023-09-01)(kurogedelic).zip", 41862 B, from hashes.db. */
    private const val TARGET_SO_ID = 0
    private const val TARGET_SIZE = 41862L
    private const val TARGET_MD5 = "05b119c37aff439c1417c1d245f7be85"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun run(workDir: File, log: (String) -> Unit) = withContext(Dispatchers.IO) {
        log("libtorrent ${org.libtorrent4j.LibTorrent.version()}")

        val torrentFile = fetchTorrent(workDir, log)
        val ti = TorrentInfo(torrentFile)
        log("torrent: ${ti.name()}, files=${ti.numFiles()}, pieceLen=${ti.pieceLength()}")

        val files = ti.files()
        val targetPath = files.filePath(TARGET_SO_ID)
        log("so_id=$TARGET_SO_ID -> $targetPath (${files.fileSize(TARGET_SO_ID)} B)")
        check(files.fileSize(TARGET_SO_ID) == TARGET_SIZE) { "size mismatch vs hashes.db" }

        downloadSelected(ti, workDir, log)

        val downloaded = File(workDir, targetPath)
        check(downloaded.isFile) { "file not found after download: $targetPath" }
        val md5 = downloaded.md5()
        log("downloaded ${downloaded.length()} B, md5=$md5")
        check(md5 == TARGET_MD5) { "md5 mismatch: expected $TARGET_MD5" }

        // spike.torrent + pobrany ROM + .<infohash>.parts (brzegowe piece'y wspoldzielone z pominietymi plikami)
        val onDisk = workDir.walkTopDown().filter { it.isFile }.toList()
        log("files written: ${onDisk.size} -> ${onDisk.joinToString { it.name }}")
        check(onDisk.size == 3) { "selective download wrote unexpected files" }
        log("SPIKE OK")
    }

    private fun fetchTorrent(workDir: File, log: (String) -> Unit): File {
        val out = File(workDir, "spike.torrent")
        if (out.isFile && out.length() > 0) {
            log("using cached .torrent (${out.length()} B)")
            return out
        }
        val url = ASSETS_BASE + TORRENT_PATH.split("/").joinToString("/") { it.urlEncode() }
        log("GET $url")
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            check(res.isSuccessful) { "HTTP ${res.code}" }
            val type = res.header("Content-Type")
            check(type == "application/x-bittorrent") { "unexpected Content-Type: $type" }
            out.outputStream().use { sink -> res.body!!.byteStream().copyTo(sink) }
        }
        log(".torrent fetched: ${out.length()} B")
        return out
    }

    private suspend fun downloadSelected(ti: TorrentInfo, workDir: File, log: (String) -> Unit) {
        val session = SessionManager()
        try {
            session.start(SessionParams(SettingsPack().apply {
                downloadRateLimit(0)
                // NIE dławić uploadu do ~0: limit obejmuje narzut protokolu (requesty, bitfield),
                // wiec klient przestaje pobierac. "Bez seedowania" = stop po zakonczeniu, nie throttling.
                uploadRateLimit(0)
                activeDownloads(1)
            }))

            val priorities = Array(ti.numFiles()) { Priority.IGNORE }
            priorities[TARGET_SO_ID] = Priority.DEFAULT
            session.download(ti, workDir, null, priorities, null, torrent_flags_t())

            val handle = session.find(ti.infoHash())
            checkNotNull(handle) { "no torrent handle" }

            for (tick in 0 until TIMEOUT_TICKS) {
                val status = handle.status()
                if (status.isFinished || status.progress() >= 1f) {
                    log("finished after ${tick}s")
                    break
                }
                if (tick % 5 == 0) {
                    log(
                        "t=${tick}s progress=${(status.progress() * 100).toInt()}% " +
                            "peers=${status.numPeers()} down=${status.downloadRate() / 1024}kB/s " +
                            "state=${status.state()}"
                    )
                }
                delay(1000)
            }
        } finally {
            session.stop()
        }
    }

    private const val TIMEOUT_TICKS = 300

    private fun File.md5(): String {
        val digest = MessageDigest.getInstance("MD5")
        inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun String.urlEncode(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
