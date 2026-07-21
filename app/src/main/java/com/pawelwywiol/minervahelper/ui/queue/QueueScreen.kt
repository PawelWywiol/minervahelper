package com.pawelwywiol.minervahelper.ui.queue

import android.os.StatFs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pawelwywiol.minervahelper.R
import com.pawelwywiol.minervahelper.data.catalog.Systems
import com.pawelwywiol.minervahelper.data.queue.DownloadQueue
import com.pawelwywiol.minervahelper.data.queue.DownloadState
import com.pawelwywiol.minervahelper.data.queue.DownloadedItem
import com.pawelwywiol.minervahelper.data.placement.Destinations
import com.pawelwywiol.minervahelper.data.queue.QueueItem
import com.pawelwywiol.minervahelper.ui.FocusableRow
import com.pawelwywiol.minervahelper.ui.MediaIcon
import com.pawelwywiol.minervahelper.ui.Screen
import com.pawelwywiol.minervahelper.ui.formatSize
import com.pawelwywiol.minervahelper.ui.initialFocus
import com.pawelwywiol.minervahelper.work.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val RECENT_DOWNLOADS = 20

private data class Group(val torrentPath: String, val items: List<QueueItem>)

@Composable
fun QueueScreen(queue: DownloadQueue, destinations: Destinations, onFolders: () -> Unit) {
    val context = LocalContext.current
    val systems = remember { Systems.get(context) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var downloaded by remember { mutableStateOf<List<DownloadedItem>>(emptyList()) }
    var confirmRemove by remember { mutableStateOf<QueueItem?>(null) }
    var confirmForget by remember { mutableStateOf<DownloadedItem?>(null) }
    LaunchedEffect(queue.revision) {
        withContext(Dispatchers.IO) {
            groups = queue.byTorrent().map { (path, items) -> Group(path, items) }
            downloaded = queue.downloaded()
        }
    }

    val items = groups.flatMap { it.items }
    val total = items.sumOf { it.size }
    val free = remember { StatFs(context.filesDir.path).availableBytes }
    val working by remember { DownloadWorker.isActiveFlow(context) }
        .collectAsState(initial = true)

    // Moving is applied to the list before the database so the drag stays under the finger; the
    // reload that the write triggers arrives at the same order.
    val move: (Int, Int) -> Unit = { from, to ->
        val target = to.coerceIn(0, groups.size - 1)
        if (from in groups.indices && target != from) {
            groups = groups.toMutableList().apply { add(target, removeAt(from)) }
            queue.moveGroup(from, target)
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        move(from.index, to.index)
    }

    // LazyColumn anchors the scroll to whichever key was on top. Reorder that key away and the list
    // slides out from under the user: a group promoted to first ends up above the viewport and
    // reads as having vanished. Someone at the top of the queue should stay at the top; anyone
    // scrolled into it follows the group they moved. Dragging needs neither — the library scrolls.
    val scope = rememberCoroutineScope()
    val moveAndFollow: (Int, Int) -> Unit = { from, to ->
        val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        move(from, to)
        scope.launch {
            if (atTop) listState.scrollToItem(0) else listState.animateScrollToItem(to)
        }
    }

    Screen("Queue") {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                "${items.size} ROMs · ${formatSize(total)} · ${groups.size} torrents",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (total > free) "Will not fit — ${formatSize(free)} free"
                else "${formatSize(free)} free",
                style = MaterialTheme.typography.bodySmall,
                color = if (total > free) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (destinations.root == null) {
                Text(
                    "No destination folder chosen — nothing can be saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onFolders) { Text("Choose folder") }
            }
            Text(
                when {
                    items.isEmpty() ->
                        "Nothing is waiting. Find a game in Browse or Search and press Download — " +
                            "it will appear here and fetch itself."
                    !working && items.any { it.state == DownloadState.QUEUED } ->
                        "Paused — press Resume to continue. Progress made so far is kept."
                    groups.size > 1 ->
                        "Games download by themselves. Drag a torrent, or use its arrows, to " +
                            "move it up the list and have it fetched sooner."
                    else -> "Games download by themselves as soon as they are added."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row {
                    if (working) {
                        OutlinedButton(onClick = { DownloadWorker.cancel(context) }) { Text("Pause all") }
                    } else if (items.any { it.state == DownloadState.QUEUED }) {
                        Button(onClick = { DownloadWorker.enqueue(context) }) { Text("Resume") }
                    }
                    if (items.any { it.state == DownloadState.FAILED }) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            queue.retryFailed()
                            DownloadWorker.enqueue(context)
                        }) {
                            Text("Retry failed")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        val firstId = items.firstOrNull()?.id
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // A whole torrent is one list item: its files are fetched together, so they move
            // together, and the drag indices are group indices with nothing to translate.
            items(groups, key = { it.torrentPath }) { group ->
                ReorderableItem(reorderState, key = group.torrentPath) {
                    val index = groups.indexOfFirst { it.torrentPath == group.torrentPath }
                    Column {
                        GroupHeader(
                            group = group,
                            active = queue.active[group.torrentPath],
                            canMoveUp = index > 0,
                            canMoveDown = index < groups.size - 1,
                            onMoveUp = { moveAndFollow(index, index - 1) },
                            onMoveDown = { moveAndFollow(index, index + 1) },
                            onTogglePause = {
                                val paused = group.items.all { it.state == DownloadState.PAUSED }
                                queue.setGroupPaused(group.torrentPath, !paused)
                                if (paused) DownloadWorker.enqueue(context)
                            },
                            dragHandle = Modifier.draggableHandle(),
                        )
                        group.items.forEach { queued ->
                            QueuedRow(
                                item = queued,
                                systems = systems,
                                destinations = destinations,
                                modifier = Modifier.initialFocus(queued.id == firstId),
                                onRemove = { confirmRemove = queued },
                                onTogglePause = {
                                    val paused = queued.state == DownloadState.PAUSED
                                    queue.setPaused(queued.id, !paused)
                                    if (paused) DownloadWorker.enqueue(context)
                                },
                            )
                        }
                    }
                }
            }
            if (downloaded.isNotEmpty()) {
                item(key = "downloaded-header") {
                    Text(
                        "Downloaded",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(downloaded.take(RECENT_DOWNLOADS), key = { "downloaded-${it.id}" }) { done ->
                    FocusableRow(
                        onClick = { confirmForget = done },
                        leading = {
                            MediaIcon(
                                systems.mediaFor(
                                    done.collection,
                                    done.platformKey,
                                    destinations.slugFor(done.collection, done.platformKey),
                                )
                            )
                        },
                    ) {
                        Text(done.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            formatSize(done.size) +
                                (done.folder.takeIf { it.isNotBlank() }?.let { " · in $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                if (downloaded.size > RECENT_DOWNLOADS) {
                    item(key = "downloaded-more") {
                        Text(
                            "…and ${downloaded.size - RECENT_DOWNLOADS} more, all marked ✓ in the lists",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    confirmForget?.let { done ->
        AlertDialog(
            onDismissRequest = { confirmForget = null },
            title = { Text("Remove from history?") },
            text = {
                Text(
                    "${done.name}\n\nThe file stays where it is — the app just forgets " +
                        "it was downloaded, and its ✓ disappears from the lists."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    queue.removeDownloaded(done.id)
                    confirmForget = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = null }) { Text("Cancel") }
            },
        )
    }

    confirmRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove from queue?") },
            text = { Text(item.name) },
            confirmButton = {
                TextButton(onClick = {
                    queue.remove(item.id)
                    confirmRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * The arrows exist because the drag handle does not: this device is driven by a d-pad, which has
 * no way to press and hold a handle. Both routes call the same move.
 */
@Composable
private fun GroupHeader(
    group: Group,
    active: DownloadQueue.Active?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onTogglePause: () -> Unit,
    dragHandle: Modifier,
) {
    val paused = group.items.all { it.state == DownloadState.PAUSED }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drag_handle),
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandle,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                group.torrentPath.substringAfterLast('/'),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Download sooner")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Download later")
            }
            PauseButton(paused = paused, label = "torrent", onClick = onTogglePause)
        }
        active?.let {
            Text(
                "${it.percent}% · ${formatSize(it.rate.toLong())}/s · ${it.peers} peers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LinearProgressIndicator(
                progress = { it.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun QueuedRow(
    item: QueueItem,
    systems: Systems,
    destinations: Destinations,
    modifier: Modifier,
    onRemove: () -> Unit,
    onTogglePause: () -> Unit,
) {
    val slug = destinations.slugFor(item.collection, item.platformKey)
    FocusableRow(
        onClick = onRemove,
        modifier = modifier,
        leading = { MediaIcon(systems.mediaFor(item.collection, item.platformKey, slug)) },
        trailing = {
            PauseButton(
                paused = item.state == DownloadState.PAUSED,
                label = item.name,
                onClick = onTogglePause,
            )
        },
    ) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${systems.labelFor(item.platformKey, slug)} · ${formatSize(item.size)} · " +
                item.state.name.lowercase(),
            style = MaterialTheme.typography.bodySmall,
        )
        item.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PauseButton(paused: Boolean, label: String, onClick: () -> Unit) = IconButton(onClick = onClick) {
    if (paused) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Resume $label")
    } else {
        Icon(painterResource(R.drawable.ic_pause), contentDescription = "Pause $label")
    }
}
