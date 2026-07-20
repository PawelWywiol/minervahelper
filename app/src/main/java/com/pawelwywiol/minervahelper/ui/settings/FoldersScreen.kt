package com.pawelwywiol.minervahelper.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pawelwywiol.minervahelper.data.placement.Destinations
import com.pawelwywiol.minervahelper.data.queue.DownloadQueue
import com.pawelwywiol.minervahelper.ui.FocusableRow
import com.pawelwywiol.minervahelper.ui.FocusedList
import com.pawelwywiol.minervahelper.ui.Screen
import com.pawelwywiol.minervahelper.ui.initialFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PlatformRow(val collection: String, val platformKey: String, val count: Int)

@Composable
fun FoldersScreen(destinations: Destinations, queue: DownloadQueue, onDev: (() -> Unit)? = null) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<PlatformRow?>(null) }

    var platforms by remember { mutableStateOf<List<PlatformRow>>(emptyList()) }
    LaunchedEffect(queue.revision) {
        platforms = withContext(Dispatchers.IO) {
            queue.all().groupBy { it.collection to it.platformKey }
                .map { (key, items) -> PlatformRow(key.first, key.second, items.size) }
                .sortedBy { it.platformKey }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            destinations.rememberRoot(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    Screen("Settings") {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                destinations.root?.let { "Root: ${it.lastPathSegment}" }
                    ?: "No folder chosen — downloads cannot be saved",
                style = MaterialTheme.typography.bodyMedium,
                color = if (destinations.root == null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = { picker.launch(null) },
                    modifier = Modifier.initialFocus(editing == null),
                ) {
                    Text(if (destinations.root == null) "Choose folder" else "Change folder")
                }
                onDev?.let {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = it) { Text("Developer tools") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        val editedRow = editing
        if (editedRow != null) {
            FolderEditor(destinations, editedRow) { editing = null }
        } else {
            // Read so an override redraws the list; folderFor itself reads preferences, not state.
            destinations.revision
            Text(
                if (platforms.isEmpty()) "Queue something to see where it will go."
                else "Where each queued platform will land — tap to change:",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FocusedList(platforms) { row, focus ->
                val mapped = destinations.isMapped(row.collection, row.platformKey)
                FocusableRow(onClick = { editing = row }, modifier = focus) {
                    Text(row.platformKey, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        destinations.folderFor(row.collection, row.platformKey) +
                            " · ${row.count} ROMs" + if (mapped) "" else " · no standard folder",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderEditor(destinations: Destinations, row: PlatformRow, onDone: () -> Unit) {
    var name by remember(row) {
        mutableStateOf(destinations.folderFor(row.collection, row.platformKey))
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(row.platformKey, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Folder name") },
            modifier = Modifier.fillMaxWidth().initialFocus(true),
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    destinations.setFolder(row.collection, row.platformKey, name)
                    onDone()
                },
            ) {
                Text("Save")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDone) { Text("Cancel") }
        }
    }
}
