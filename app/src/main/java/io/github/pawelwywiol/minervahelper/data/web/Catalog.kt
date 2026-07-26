package io.github.pawelwywiol.minervahelper.data.web

import android.content.Context
import io.github.pawelwywiol.minervahelper.data.catalog.CatalogReader
import java.io.File

/**
 * Opens the catalog for the length of one request and closes it again. A reader held open would
 * outlive the file: a re-import deletes and rebuilds catalog.db, and the server would then be
 * reading a database that no longer exists. Opening read-only SQLite is a file handle and a header
 * read, so this is cheap enough to pay per request.
 *
 * Null when there is no catalog yet — the browser is told to import it on the device.
 */
internal fun <T> withCatalog(context: Context, block: (CatalogReader) -> T): T? {
    val file = File(context.filesDir, "catalog.db")
    if (!file.isFile) return null
    return runCatching { CatalogReader(file).use(block) }.getOrNull()
}
