package com.pawelwywiol.minervahelper.ui.catalog

import androidx.compose.runtime.mutableStateListOf
import com.pawelwywiol.minervahelper.data.catalog.CatalogReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PAGE = 50

/**
 * Feeds one platform's ROMs into a list a page at a time. Pages continue from the last row already
 * held rather than by row number: on the target device an OFFSET deep into a 753k-row platform
 * costs 313 ms, while seeking to a key stays flat.
 */
class RomPager(private val reader: CatalogReader, private val platformId: Long) {

    val roms = mutableStateListOf<CatalogReader.Rom>()
    private var exhausted = false
    private var loading = false

    suspend fun loadMore() {
        if (exhausted || loading) return
        loading = true
        try {
            val page = withContext(Dispatchers.IO) { reader.browse(platformId, roms.lastOrNull(), PAGE) }
            roms += page
            exhausted = page.size < PAGE
        } finally {
            loading = false
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1_000 -> "${bytes / 1_000} kB"
    else -> "$bytes B"
}

fun formatCount(count: Long): String =
    count.toString().reversed().chunked(3).joinToString(" ").reversed()
