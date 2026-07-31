package io.github.pawelwywiol.minervahelper.data.web

import android.content.Context
import io.github.pawelwywiol.minervahelper.data.catalog.CatalogReader
import java.io.File

/**
 * Opens the catalog for the length of one request; the caller closes it. A reader held open would
 * outlive the file, because a re-import deletes and rebuilds catalog.db and the server would go on
 * reading a database that no longer exists. Opening read-only SQLite is a file handle and a header
 * read, so this is cheap enough to pay per request.
 *
 * Null means "no catalog worth reading" — absent, half-imported, or built by an older schema. One
 * definition, so no route has to decide for itself what counts as ready.
 */
internal fun openCatalog(context: Context): CatalogReader? {
    val file = File(context.filesDir, "catalog.db")
    if (!file.isFile) return null
    val reader = runCatching { CatalogReader(file) }.getOrNull() ?: return null
    if (runCatching { reader.isComplete() }.getOrDefault(false)) return reader
    reader.close()
    return null
}
