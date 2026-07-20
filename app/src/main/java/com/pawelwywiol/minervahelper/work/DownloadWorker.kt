package com.pawelwywiol.minervahelper.work

import android.content.Context
import android.os.StatFs
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pawelwywiol.minervahelper.data.download.SelectiveDownloader
import com.pawelwywiol.minervahelper.data.download.TorrentCache
import com.pawelwywiol.minervahelper.data.download.loadTorrentInfo
import com.pawelwywiol.minervahelper.data.download.md5
import com.pawelwywiol.minervahelper.data.queue.DownloadQueue
import com.pawelwywiol.minervahelper.data.queue.DownloadState
import com.pawelwywiol.minervahelper.data.placement.Destinations
import com.pawelwywiol.minervahelper.data.placement.FilePlacement
import com.pawelwywiol.minervahelper.data.queue.QueueItem
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import com.pawelwywiol.minervahelper.ui.formatSize
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val TAG = "MinervaDownload"

/**
 * Works through the queue one torrent at a time, downloading only the selected files and verifying
 * each against the checksum the archive published before marking it done.
 *
 * Unmetered-only: these are ROM-sized transfers and must never land on a mobile plan.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val queue by lazy { DownloadQueue.get(applicationContext) }
    private val downloader = SelectiveDownloader()
    private val torrents by lazy {
        TorrentCache(File(applicationContext.filesDir, "torrents"), OkHttpClient())
    }
    private val destinations by lazy { Destinations.get(applicationContext) }
    private val placement by lazy { FilePlacement(applicationContext) }

    override suspend fun doWork(): Result {
        Notifications.ensureChannels(applicationContext)
        // Checked before anything is fetched: downloading gigabytes only to have nowhere to put them
        // wastes the user's bandwidth and hides the real problem behind a failed item.
        if (destinations.root == null) {
            Log.e(TAG, "no destination folder chosen")
            return Result.failure()
        }
        queue.resetInterrupted()
        // Only queued items: failed ones wait for an explicit retry, otherwise a permanently
        // broken file would re-download on every run the queue chains up.
        val pending = queue.byTorrent()
            .mapValues { (_, items) -> items.filter { it.state == DownloadState.QUEUED } }
            .filterValues { it.isNotEmpty() }
        if (pending.isEmpty()) return Result.success()

        return try {
            pending.forEach { (torrentPath, items) -> downloadGroup(torrentPath, items) }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "queue run failed", error)
            Result.failure()
        }
    }

    private suspend fun downloadGroup(torrentPath: String, items: List<QueueItem>) {
        items.forEach { queue.setState(it.id, DownloadState.DOWNLOADING) }
        val stagingDir = File(applicationContext.filesDir, "downloads")
        val label = torrentPath.substringAfterLast('/').removeSuffix(".torrent")

        try {
            val needed = items.sumOf { it.size }
            val free = StatFs(applicationContext.filesDir.path).availableBytes
            if (needed > free) {
                throw OutOfSpace(needed, free)
            }
            val torrent = torrents.fetch(torrentPath)
            report(label, 0)

            val result = downloader.download(torrent, items.map { it.soId }.toSet(), stagingDir) { p ->
                Log.i(TAG, "${p.percent()}% ${p.done}/${p.total} rate=${p.rate} peers=${p.peers} ${p.detail}")
                queue.active = DownloadQueue.Active(label, p.percent(), p.rate, p.peers)
                report(label, p.percent())
            }
            Log.i(TAG, "payload uploaded=${result.payloadUploaded} B, protocol=${result.protocolOverhead} B")
            // The hard rule is checked, not assumed. Protocol overhead is unavoidable — a peer that
            // sends no requests downloads nothing — but file content must be exactly zero.
            check(result.payloadUploaded == 0L) {
                "shared ${result.payloadUploaded} bytes of file content — non-sharing guarantee broken"
            }

            val info = loadTorrentInfo(torrent)
            items.forEach { verifyAndPlace(it, File(stagingDir, info.files().filePath(it.soId))) }
            // libtorrent leaves a .parts scratch file and the torrent's directory tree behind. They
            // are dead once the wanted files are placed, and nothing else clears them — measured at
            // 8 MB after a single 54 MB download. Only on the success path: an interrupted download
            // keeps its partial data, which is what lets it resume.
            stagingDir.deleteRecursively()
        } catch (cancelled: CancellationException) {
            items.forEach { queue.setState(it.id, DownloadState.QUEUED) }
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "torrent failed: $torrentPath", error)
            val reason = reason(error)
            items.forEach { queue.setState(it.id, DownloadState.FAILED, reason) }
        } finally {
            queue.active = null
        }
    }

    /**
     * Plain language, and specific enough to act on. "Failed" alone tells the user nothing about
     * whether to wait, free up space, or give up on that file.
     */
    private fun reason(error: Throwable): String = when (error) {
        is OutOfSpace -> "Not enough space — needs ${formatSize(error.needed)}, " +
            "${formatSize(error.free)} free"
        is SelectiveDownloader.Stalled ->
            if (error.peers == 0) "No one is sharing this file right now — try again later"
            else "The download stopped making progress — try again later"
        is UnknownHostException, is SocketTimeoutException, is ConnectException ->
            "No connection — check Wi-Fi and try again"
        is IOException -> "Connection problem — ${error.message.orEmpty().ifEmpty { "try again" }}"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
    }

    private class OutOfSpace(val needed: Long, val free: Long) :
        Exception("needs $needed bytes, $free free")

    private fun verifyAndPlace(item: QueueItem, file: File) {
        queue.setState(item.id, DownloadState.VERIFYING)
        val expected = item.md5
        val actual = if (file.isFile) file.md5() else null
        if (!file.isFile || file.length() != item.size || (expected != null && expected != actual)) {
            Log.e(TAG, "verification failed for ${item.name}: expected $expected, got $actual")
            // A file that failed its checksum is deleted rather than kept: left in staging it would
            // be mistaken for a finished download on the next run.
            file.delete()
            queue.setState(
                item.id, DownloadState.FAILED,
                "The downloaded file was damaged — remove it and try again",
            )
            return
        }
        val failure = placementFailure(item, file)
        if (failure == null) {
            queue.markDownloaded(item, destinations.folderFor(item.collection, item.platformKey))
        } else {
            queue.setState(item.id, DownloadState.FAILED, failure)
        }
    }

    /** Null when the file was placed; otherwise why it could not be. */
    private fun placementFailure(item: QueueItem, file: File): String? {
        val root = destinations.root ?: return "No destination folder chosen"
        val folder = destinations.folderFor(item.collection, item.platformKey)
        return runCatching { placement.place(root, folder, item.name, file) }
            .fold(
                onSuccess = { Log.i(TAG, "placed ${item.name} in $folder"); null },
                onFailure = {
                    Log.e(TAG, "cannot place ${item.name} in $folder", it)
                    "Could not save into $folder — ${it.message.orEmpty().ifEmpty { "check the folder" }}"
                },
            )
    }

    private suspend fun report(label: String, percent: Int) = setForeground(
        ForegroundInfo(
            Notifications.ID_DOWNLOAD,
            Notifications.progress(applicationContext, label, percent),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    )

    companion object {
        const val NAME = "downloads"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .build()
            // Append rather than replace: adding a game must not kill a download already running.
            // Each add chains one more run; a run that finds nothing pending costs nothing.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

private fun SelectiveDownloader.Progress.percent(): Int =
    if (total > 0) (done * 100 / total).toInt() else 0
