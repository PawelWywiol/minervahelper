package com.pawelwywiol.minervahelper.data.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.add_torrent_params
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.load_torrent_limits
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t
import org.libtorrent4j.swig.torrent_info
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

private const val ASSETS_BASE = "https://minerva-archive.org/assets/"
private const val STALL_LIMIT_SECONDS = 300

/**
 * Fetches the `.torrent` files the queue refers to, keeping them so a retry does not re-download
 * metadata that can reach 27 MB.
 */
class TorrentCache(private val dir: File, private val http: OkHttpClient) {

    fun fetch(torrentPath: String): File {
        dir.mkdirs()
        val out = File(dir, torrentPath.replace(Regex("[^A-Za-z0-9.-]"), "_"))
        if (out.isFile && out.length() > 0) return out

        val url = ASSETS_BASE + torrentPath.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            check(res.isSuccessful) { "HTTP ${res.code} for $torrentPath" }
            val partial = File(out.path + ".part")
            partial.outputStream().use { sink -> checkNotNull(res.body).byteStream().copyTo(sink) }
            check(partial.renameTo(out)) { "cannot finalise ${out.name}" }
        }
        return out
    }
}

/**
 * Downloads only the requested files out of a torrent.
 *
 * **The project's hard rule — never share files — is enforced here, structurally:**
 * 1. incoming connections are refused (TCP and uTP), so no peer can ever open a connection to
 *    this device and ask for data;
 * 2. the unchoke limit is zero, so no peer is ever permitted to receive file content on a
 *    connection we opened either;
 * 3. the torrent is removed as soon as the wanted files are complete — it never becomes a seed;
 * 4. [Result.payloadUploaded] is checked by the caller and must be 0.
 *
 * Uploading is *not* prevented with an upload rate limit: that limit also covers protocol
 * overhead, which stops the client from even requesting data (proven in M0 — two minutes,
 * zero bytes, healthy swarm).
 *
 * DHT and local peer discovery stay **on**, deliberately. Measured on the target device: with them
 * off, this archive's torrents find zero peers in five minutes — the public trackers listed in the
 * files are dead, so DHT is the only working way to find a seed. DHT does make this device
 * discoverable, but discovery is not sharing: an inbound connection cannot be established, and an
 * outbound one is never unchoked.
 */
class SelectiveDownloader {

    /** No peer offered any data for [STALL_LIMIT_SECONDS]; [peers] is how many were connected. */
    class Stalled(val peers: Int) : Exception("no progress for ${STALL_LIMIT_SECONDS}s (peers=$peers)")

    data class Result(val payloadUploaded: Long, val protocolOverhead: Long)

    data class Progress(
        val done: Long,
        val total: Long,
        val rate: Int,
        val peers: Int,
        val detail: String = "",
    )

    suspend fun download(
        torrent: File,
        soIds: Set<Int>,
        targetDir: File,
        onProgress: suspend (Progress) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val info = loadTorrentInfo(torrent)
        val wanted = soIds.filter { it in 0 until info.numFiles() }.toSet()
        require(wanted.isNotEmpty()) { "no valid file indexes for ${torrent.name}" }

        targetDir.mkdirs()
        val session = SessionManager()
        var uploaded = 0L
        var overhead = 0L
        try {
            session.start(SessionParams(noSharingSettings()))

            val priorities = Array(info.numFiles()) { Priority.IGNORE }
            wanted.forEach { priorities[it] = Priority.DEFAULT }
            session.download(info, targetDir, null, priorities, null, torrent_flags_t())

            val handle = checkNotNull(session.find(info.infoHash())) { "no torrent handle" }
            val total = wanted.sumOf { info.files().fileSize(it) }
            var stalledFor = 0
            var lastDone = -1L

            while (true) {
                coroutineContext.ensureActive()
                val status = handle.status()
                uploaded = status.totalPayloadUpload()
                val done = (status.progress() * total).toLong()

                if (status.isFinished || status.progress() >= 1f) {
                    overhead = status.totalUpload()
                    break
                }
                stalledFor = if (done == lastDone) stalledFor + 1 else 0
                lastDone = done
                if (stalledFor >= STALL_LIMIT_SECONDS) throw Stalled(status.numPeers())

                onProgress(
                    Progress(
                        done, total, status.downloadRate(), status.numPeers(),
                        detail = "state=${status.state()} seeds=${status.numSeeds()} " +
                            "conn=${status.numConnections()} up=${status.totalUpload()}",
                    )
                )
                delay(1000)
            }
            onProgress(Progress(total, total, 0, 0))
            // Removed before the session is even stopped: a finished torrent left in a running
            // session is, by definition, a seed.
            session.remove(handle)
            Result(payloadUploaded = uploaded, protocolOverhead = overhead)
        } finally {
            session.stop()
        }
    }

    private fun noSharingSettings() = SettingsPack().apply {
        downloadRateLimit(0)
        uploadRateLimit(0)
        activeDownloads(1)
        activeSeeds(0)
        setBoolean(settings_pack.bool_types.enable_incoming_tcp.swigValue(), false)
        setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), false)
        setInteger(settings_pack.int_types.unchoke_slots_limit.swigValue(), 0)
    }
}

/**
 * [TorrentInfo]'s own parser rejects this archive's biggest torrents — 27 MB of metadata and
 * 305k files exceed its built-in bencode caps ("item count limit exceeded"). These limits are
 * sanity bounds checked during parsing, not allocations. The parsed torrent_info is copied
 * because add_torrent_params owns the original and would free it when garbage collected.
 */
fun loadTorrentInfo(torrent: File): TorrentInfo {
    val limits = load_torrent_limits().apply {
        max_buffer_size = 256 * 1024 * 1024
        max_decode_tokens = 50_000_000
    }
    val ec = error_code()
    val params = add_torrent_params.load_torrent_file(torrent.absolutePath, ec, limits)
    check(ec.value() == 0) { "cannot parse ${torrent.name}: ${ec.message()}" }
    return TorrentInfo(torrent_info(params.ti_ptr()))
}

fun File.md5(): String {
    val digest = MessageDigest.getInstance("MD5")
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
