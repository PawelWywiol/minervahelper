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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pawelwywiol.minervahelper.data.queue.DownloadQueue
import com.pawelwywiol.minervahelper.data.queue.DownloadState
import com.pawelwywiol.minervahelper.data.queue.DownloadedItem
import com.pawelwywiol.minervahelper.data.placement.Destinations
import com.pawelwywiol.minervahelper.data.queue.QueueItem
import com.pawelwywiol.minervahelper.ui.FocusableRow
import com.pawelwywiol.minervahelper.ui.Screen
import com.pawelwywiol.minervahelper.ui.formatSize
import com.pawelwywiol.minervahelper.ui.initialFocus
import com.pawelwywiol.minervahelper.work.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RECENT_DOWNLOADS = 20

@Composable
fun QueueScreen(queue: DownloadQueue, destinations: Destinations, onFolders: () -> Unit) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<Map<String, List<QueueItem>>>(emptyMap()) }
    var downloaded by remember { mutableStateOf<List<DownloadedItem>>(emptyList()) }
    var confirmRemove by remember { mutableStateOf<QueueItem?>(null) }
    LaunchedEffect(queue.revision) {
        withContext(Dispatchers.IO) {
            groups = queue.byTorrent()
            downloaded = queue.downloaded()
        }
    }

    val items = groups.values.flatten()
    val total = items.sumOf { it.size }
    val free = remember { StatFs(context.filesDir.path).availableBytes }
    val working by remember { DownloadWorker.isActiveFlow(context) }
        .collectAsState(initial = true)

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
            queue.active?.let {
                Text(
                    "${it.label} — ${it.percent}% · ${formatSize(it.rate.toLong())}/s · ${it.peers} peers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(
                    progress = { it.percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
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
                    else -> "Games download by themselves as soon as they are added."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row {
                    if (working) {
                        OutlinedButton(onClick = { DownloadWorker.cancel(context) }) { Text("Pause") }
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            groups.forEach { (torrentPath, group) ->
                item(key = torrentPath) {
                    Text(
                        torrentPath.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(group, key = { it.id }) { queued ->
                    FocusableRow(
                        onClick = { confirmRemove = queued },
                        modifier = Modifier.initialFocus(queued.id == firstId),
                    ) {
                        Text(queued.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${formatSize(queued.size)} · ${queued.state.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        queued.error?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
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
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
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
