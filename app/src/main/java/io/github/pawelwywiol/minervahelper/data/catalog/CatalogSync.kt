package io.github.pawelwywiol.minervahelper.data.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * One-shot catalog bootstrap: fetch upstream hashes.db, normalize it into catalog.db,
 * then drop the 1.64 GB source. The download resumes after interruption; the import
 * restarts from scratch (see CatalogImporter).
 */
class CatalogSync(
    private val context: Context,
    private val http: OkHttpClient,
) {

    sealed interface Stage {
        data class Downloading(val done: Long, val total: Long) : Stage
        data class Importing(val done: Long, val total: Long) : Stage
        data object Ready : Stage
    }

    val catalogFile: File get() = File(context.filesDir, "catalog.db")
    private val sourceFile: File get() = File(context.cacheDir, "hashes.db")

    fun isReady(): Boolean = catalogFile.isFile &&
        runCatching { CatalogReader(catalogFile).use { it.isComplete() } }.getOrDefault(false)

    /**
     * Whether the archive has published a newer index than the one this catalog was built from.
     * Costs one HEAD request. A catalog built before versions were recorded reports false: nagging
     * about an update we cannot confirm is worse than staying quiet.
     */
    suspend fun hasUpdate(): Boolean = withContext(Dispatchers.IO) {
        val built = runCatching {
            CatalogReader(catalogFile).use { it.sourceVersion() }
        }.getOrNull() ?: return@withContext false
        val upstream = runCatching { HashesDownloader(http).version() }.getOrNull()
        upstream != null && upstream != built
    }

    /**
     * [force] rebuilds even when a complete catalog is already present — used when the archive has
     * published a newer index (R34).
     */
    suspend fun run(force: Boolean = false, onStage: suspend (Stage) -> Unit) {
        if (!force && isReady()) {
            onStage(Stage.Ready)
            return
        }
        val downloader = HashesDownloader(http)
        val version = runCatching { downloader.version() }.getOrNull()
        downloader.download(sourceFile) { onStage(Stage.Downloading(it.downloaded, it.total)) }
        CatalogImporter().import(sourceFile, catalogFile, version) {
            onStage(Stage.Importing(it.imported, it.total))
        }
        sourceFile.delete()
        File(sourceFile.parentFile, sourceFile.name + ".etag").delete()
        onStage(Stage.Ready)
    }
}
