package com.pawelwywiol.minervahelper.ui.queue

import android.os.StatFs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.pawelwywiol.minervahelper.data.queue.QueueItem
import com.pawelwywiol.minervahelper.ui.FocusableRow
import com.pawelwywiol.minervahelper.ui.Screen
import com.pawelwywiol.minervahelper.ui.formatSize
import com.pawelwywiol.minervahelper.ui.initialFocus
import com.pawelwywiol.minervahelper.work.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QueueScreen(queue: DownloadQueue) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<Map<String, List<QueueItem>>>(emptyMap()) }
    LaunchedEffect(queue.revision) { groups = withContext(Dispatchers.IO) { queue.byTorrent() } }

    val items = groups.values.flatten()
    val total = items.sumOf { it.size }
    val free = remember { StatFs(context.filesDir.path).availableBytes }

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
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    enabled = items.any { it.state != DownloadState.DONE },
                    onClick = { DownloadWorker.enqueue(context) },
                ) {
                    Text("Download")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { DownloadWorker.cancel(context) }) { Text("Stop") }
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
                        onClick = { queue.remove(queued.id) },
                        modifier = Modifier.initialFocus(queued.id == firstId),
                    ) {
                        Text(queued.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${formatSize(queued.size)} · ${queued.state.name.lowercase()} · tap to remove",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
