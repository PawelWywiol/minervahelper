package com.pawelwywiol.minervahelper.ui.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pawelwywiol.minervahelper.data.catalog.CatalogReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PAGE = 50

/**
 * The most rows kept in memory at once. The biggest platform has 753k entries — without a cap a
 * long scroll would slowly hold them all. Rows trimmed off either end are reloaded from the
 * catalogue when the user scrolls back; LazyColumn keeps the scroll position by row key, so
 * trimming outside the viewport is invisible.
 */
private const val MAX_ROWS = 1_000

/**
 * Feeds one platform's ROMs into a list a page at a time. Pages continue from the last row already
 * held rather than by row number: on the target device an OFFSET deep into a 753k-row platform
 * costs 313 ms, while seeking to a key stays flat.
 */
class RomPager(private val reader: CatalogReader, private val platformId: Long) {

    val roms = mutableStateListOf<CatalogReader.Rom>()

    /** True when rows were trimmed off the top; scrolling up reloads them. */
    var trimmedAbove by mutableStateOf(false)
        private set

    private var exhausted = false
    private var loading = false

    suspend fun loadMore() {
        if (exhausted || loading) return
        loading = true
        try {
            val page = withContext(Dispatchers.IO) { reader.browse(platformId, roms.lastOrNull(), PAGE) }
            roms += page
            exhausted = page.size < PAGE
            if (roms.size > MAX_ROWS) {
                roms.removeRange(0, roms.size - MAX_ROWS)
                trimmedAbove = true
            }
        } finally {
            loading = false
        }
    }

    suspend fun loadBefore() {
        if (!trimmedAbove || loading) return
        loading = true
        try {
            val first = roms.firstOrNull() ?: return
            val page = withContext(Dispatchers.IO) { reader.browseBefore(platformId, first, PAGE) }
            roms.addAll(0, page)
            if (page.size < PAGE) trimmedAbove = false
            if (roms.size > MAX_ROWS) {
                roms.removeRange(MAX_ROWS, roms.size)
                exhausted = false
            }
        } finally {
            loading = false
        }
    }
}
