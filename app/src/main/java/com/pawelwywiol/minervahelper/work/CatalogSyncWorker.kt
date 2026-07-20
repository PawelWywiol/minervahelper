package com.pawelwywiol.minervahelper.work

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pawelwywiol.minervahelper.R
import com.pawelwywiol.minervahelper.data.catalog.CatalogSync
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient

/**
 * Bootstraps the catalog in the background. Constrained to unmetered networks —
 * the source is 1.64 GB and must never land on a mobile plan by accident.
 */
class CatalogSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private var lastReported: String? = null

    override suspend fun doWork(): Result {
        Notifications.ensureChannels(applicationContext)
        val sync = CatalogSync(applicationContext, OkHttpClient())
        val force = inputData.getBoolean(KEY_FORCE, false)
        return try {
            sync.run(force) { stage -> report(stage) }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    /** Progress fires per download chunk and per import batch; only whole percents reach the system. */
    private suspend fun report(stage: CatalogSync.Stage) {
        val (stageId, percent) = when (stage) {
            is CatalogSync.Stage.Downloading -> DOWNLOADING to stage.percent()
            is CatalogSync.Stage.Importing -> IMPORTING to stage.percent()
            CatalogSync.Stage.Ready -> READY to 100
        }
        val key = "$stageId $percent"
        if (key == lastReported) return
        lastReported = key

        val label = applicationContext.getString(
            when (stageId) {
                DOWNLOADING -> R.string.sync_downloading
                IMPORTING -> R.string.sync_importing
                else -> R.string.sync_ready
            }
        )
        setProgress(workDataOf(KEY_STAGE to stageId, KEY_PERCENT to percent))
        setForeground(
            ForegroundInfo(
                Notifications.ID_SYNC,
                Notifications.progress(applicationContext, label, percent),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        )
    }

    companion object {
        const val NAME = "catalog-sync"
        const val KEY_STAGE = "stage"
        const val KEY_PERCENT = "percent"
        const val KEY_FORCE = "force"
        private const val MAX_ATTEMPTS = 5
        private const val DOWNLOADING = "downloading"
        private const val IMPORTING = "importing"
        private const val READY = "ready"

        fun enqueue(context: Context, force: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<CatalogSyncWorker>()
                .setInputData(workDataOf(KEY_FORCE to force))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

private fun CatalogSync.Stage.Downloading.percent() =
    if (total > 0) (done * 100 / total).toInt() else 0

private fun CatalogSync.Stage.Importing.percent() =
    if (total > 0) (done * 100 / total).toInt() else 0
