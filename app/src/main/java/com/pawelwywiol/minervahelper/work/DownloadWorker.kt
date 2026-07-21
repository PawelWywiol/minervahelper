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
import com.pawelwywiol.minervahelper.data.settings.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val preferences by lazy { Preferences.get(applicationContext) }
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
        if (pendingGroups().isEmpty()) return Result.success()

        val limit = preferences.concurrentDownloads
        return try {
            SelectiveDownloader(maxConcurrent = limit).use { downloader ->
                coroutineScope {
                    val reporter = launch { reportProgress() }
                    // Re-read between batches rather than working from one snapshot: a game added
                    // while this run is going would otherwise wait for a whole separate run, which
                    // reads as "downloads at once: 2" quietly doing one. Failed and paused groups
                    // are not pending, so this terminates.
                    while (true) {
                        val pending = pendingGroups()
                        if (pending.isEmpty()) break
                        // The semaphore consumes `pending` in order, so a freed slot always goes to
                        // the highest-priority torrent waiting — that is what the queue's order means.
                        val slots = Semaphore(limit)
                        pending.map { (torrentPath, items) ->
                            async { slots.withPermit { downloadGroup(downloader, torrentPath, items) } }
                        }.awaitAll()
                    }
                    reporter.cancel()
                }
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "queue run failed", error)
            Result.failure()
        } finally {
            queue.clearActive()
        }
    }

    /**
     * Only queued items: failed ones wait for an explicit retry, otherwise a permanently broken
     * file would re-download on every run the queue chains up, and paused ones are the user saying
     * "not now".
     */
    private fun pendingGroups(): Map<String, List<QueueItem>> = queue.byTorrent()
        .mapValues { (_, items) -> items.filter { it.state == DownloadState.QUEUED } }
        .filterValues { it.isNotEmpty() }

    private suspend fun downloadGroup(
        downloader: SelectiveDownloader,
        torrentPath: String,
        items: List<QueueItem>,
    ) {
        items.forEach { queue.setState(it.id, DownloadState.DOWNLOADING) }
        // One staging directory per torrent: downloads now run side by side, and the tidy-up below
        // would otherwise delete a sibling's partial data along with its own.
        val stagingDir = File(applicationContext.filesDir, "downloads/${stagingName(torrentPath)}")
        val label = torrentPath.substringAfterLast('/').removeSuffix(".torrent")

        try {
            val needed = items.sumOf { it.size }
            val free = StatFs(applicationContext.filesDir.path).availableBytes
            if (needed > free) {
                throw OutOfSpace(needed, free)
            }
            val torrent = torrents.fetch(torrentPath)

            val result = downloader.download(torrent, stagingDir, { stillWanted(items) }) { p ->
                Log.i(TAG, "$label ${p.percent()}% ${p.done}/${p.total} rate=${p.rate} peers=${p.peers} ${p.detail}")
                queue.setActive(torrentPath, DownloadQueue.Active(label, p.percent(), p.rate, p.peers))
            }
            Log.i(TAG, "$label payload uploaded=${result.payloadUploaded} B, protocol=${result.protocolOverhead} B")
            // The hard rule is checked, not assumed, and per torrent. Protocol overhead is
            // unavoidable — a peer that sends no requests downloads nothing — but file content
            // must be exactly zero.
            check(result.payloadUploaded == 0L) {
                "shared ${result.payloadUploaded} bytes of file content — non-sharing guarantee broken"
            }

            val info = loadTorrentInfo(torrent)
            val wanted = stillWanted(items)
            val finished = items.filter { it.soId in wanted }
            finished.forEach { verifyAndPlace(it, File(stagingDir, info.files().filePath(it.soId))) }
            // libtorrent leaves a .parts scratch file and the torrent's directory tree behind. They
            // are dead once the wanted files are placed, and nothing else clears them — measured at
            // 8 MB after a single 54 MB download. Only on the success path: an interrupted download
            // keeps its partial data, which is what lets it resume.
            if (finished.size == items.size) stagingDir.deleteRecursively()
        } catch (paused: SelectiveDownloader.AllPaused) {
            Log.i(TAG, "all files paused: $torrentPath")
        } catch (cancelled: CancellationException) {
            items.forEach { queue.setState(it.id, DownloadState.QUEUED) }
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "torrent failed: $torrentPath", error)
            val reason = reason(error)
            items.forEach { queue.setState(it.id, DownloadState.FAILED, reason) }
        } finally {
            queue.setActive(torrentPath, null)
        }
    }

    /** Re-read from the queue every tick so a pause pressed mid-download is honoured. */
    private fun stillWanted(items: List<QueueItem>): Set<Int> {
        val paused = queue.pausedIds()
        return items.filterNot { it.id in paused }.mapTo(mutableSetOf()) { it.soId }
    }

    /**
     * One notification for the whole run, refreshed on a timer. Several downloads report at once
     * and `setForeground` cannot be called concurrently.
     */
    private suspend fun reportProgress() {
        while (true) {
            val running = queue.active.values
            val text = when (running.size) {
                0 -> "Starting…"
                1 -> running.first().let { "${it.label} — ${it.percent}%" }
                else -> "${running.size} downloads — ${running.sumOf { it.percent } / running.size}%"
            }
            val percent = running.takeIf { it.isNotEmpty() }?.let { it.sumOf { a -> a.percent } / it.size }
            report(text, percent ?: 0)
            delay(1000)
        }
    }

    /** Torrent paths contain separators and spaces; the staging folder must survive both. */
    private fun stagingName(torrentPath: String) =
        torrentPath.replace(Regex("[^A-Za-z0-9.-]"), "_")

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
            Notifications.downloadProgress(applicationContext, label, percent),
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

        /** True while the queue is being worked on or is waiting for its constraints (Wi-Fi). */
        fun isActiveFlow(context: Context): Flow<Boolean> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NAME)
                .map { infos -> infos.any { !it.state.isFinished } }
    }
}

private fun SelectiveDownloader.Progress.percent(): Int =
    if (total > 0) (done * 100 / total).toInt() else 0
