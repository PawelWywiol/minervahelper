package io.github.pawelwywiol.minervahelper.data.placement

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.pawelwywiol.minervahelper.data.queue.DownloadQueue
import io.github.pawelwywiol.minervahelper.data.queue.DownloadedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Moves downloaded files when the user changes a folder (R39). Runs in its own scope rather than
 * the screen's: a library move must not stop because the user navigated away mid-way.
 */
object FileMover {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var status by mutableStateOf<String?>(null)
        private set

    fun moveAll(
        placement: FilePlacement,
        queue: DownloadQueue,
        destinations: Destinations,
        fromRoot: Uri,
        toRoot: Uri,
    ) {
        scope.launch {
            status = "Moving files…"
            var moved = 0
            var missing = 0
            var failed = 0
            queue.downloaded().forEach { item ->
                val folder = item.folder.ifBlank {
                    destinations.folderFor(item.collection, item.platformKey)
                }
                runCatching { placement.move(fromRoot, folder, toRoot, folder, item.name) }
                    .fold({ if (it) moved++ else missing++ }, { failed++ })
            }
            status = buildString {
                append("Moved $moved files")
                if (missing > 0) append(" · $missing not found")
                if (failed > 0) append(" · $failed failed")
            }
        }
    }

    fun moveBetweenFolders(
        placement: FilePlacement,
        queue: DownloadQueue,
        root: Uri,
        from: String,
        to: String,
        items: List<DownloadedItem>,
    ) {
        scope.launch {
            status = "Moving files…"
            var moved = 0
            var failed = 0
            items.forEach { item ->
                runCatching { placement.move(root, from, root, to, item.name) }
                    .fold(
                        { if (it) { moved++; queue.setDownloadedFolder(item.id, to) } },
                        { failed++ },
                    )
            }
            status = "Moved $moved files" + if (failed > 0) " · $failed failed" else ""
        }
    }
}
