package com.pawelwywiol.minervahelper.data.catalog

import android.content.Context
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

    suspend fun run(onStage: suspend (Stage) -> Unit) {
        if (isReady()) {
            onStage(Stage.Ready)
            return
        }
        HashesDownloader(http).download(sourceFile) { onStage(Stage.Downloading(it.downloaded, it.total)) }
        CatalogImporter().import(sourceFile, catalogFile) { onStage(Stage.Importing(it.imported, it.total)) }
        sourceFile.delete()
        File(sourceFile.parentFile, sourceFile.name + ".etag").delete()
        onStage(Stage.Ready)
    }
}
