package com.pawelwywiol.minervahelper.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pawelwywiol.minervahelper.data.placement.Destinations
import com.pawelwywiol.minervahelper.data.placement.FileMover
import com.pawelwywiol.minervahelper.data.placement.FilePlacement
import com.pawelwywiol.minervahelper.data.queue.DownloadQueue
import com.pawelwywiol.minervahelper.data.queue.DownloadedItem
import com.pawelwywiol.minervahelper.ui.FocusableRow
import com.pawelwywiol.minervahelper.ui.FocusedList
import com.pawelwywiol.minervahelper.ui.Screen
import com.pawelwywiol.minervahelper.ui.initialFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PlatformRow(val collection: String, val platformKey: String, val count: Int)

private data class PendingFolderMove(val from: String, val to: String, val items: List<DownloadedItem>)

private val GRANT_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@Composable
fun FoldersScreen(destinations: Destinations, queue: DownloadQueue, onDev: (() -> Unit)? = null) {
    val context = LocalContext.current
    val placement = remember { FilePlacement(context) }
    var editing by remember { mutableStateOf<PlatformRow?>(null) }
    var pendingRoot by remember { mutableStateOf<Uri?>(null) }
    var pendingFolderMove by remember { mutableStateOf<PendingFolderMove?>(null) }

    var platforms by remember { mutableStateOf<List<PlatformRow>>(emptyList()) }
    LaunchedEffect(queue.revision) {
        platforms = withContext(Dispatchers.IO) {
            val queued = queue.all().groupBy { it.collection to it.platformKey }
            val done = queue.downloaded().groupBy { it.collection to it.platformKey }
            (queued.keys + done.keys).map { key ->
                PlatformRow(
                    key.first, key.second,
                    (queued[key]?.size ?: 0) + (done[key]?.size ?: 0),
                )
            }.sortedBy { it.platformKey }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            val current = destinations.root
            // Changing the root with downloads in place raises the move-or-leave question (R39);
            // the root is only committed once the user has answered it.
            if (current != null && current != it && queue.downloaded().isNotEmpty()) pendingRoot = it
            else destinations.rememberRoot(it, GRANT_FLAGS)
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
            FileMover.status?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))

        val editedRow = editing
        if (editedRow != null) {
            FolderEditor(
                destinations, editedRow,
                onSave = { newFolder ->
                    val old = destinations.folderFor(editedRow.collection, editedRow.platformKey)
                    destinations.setFolder(editedRow.collection, editedRow.platformKey, newFolder)
                    if (newFolder != old && destinations.root != null) {
                        val affected = queue.downloaded().filter {
                            it.collection == editedRow.collection &&
                                it.platformKey == editedRow.platformKey &&
                                it.folder.ifBlank { old } == old
                        }
                        if (affected.isNotEmpty()) {
                            pendingFolderMove = PendingFolderMove(old, newFolder, affected)
                        }
                    }
                    editing = null
                },
                onCancel = { editing = null },
            )
        } else {
            // Read so an override redraws the list; folderFor itself reads preferences, not state.
            destinations.revision
            Text(
                if (platforms.isEmpty()) "Queue something to see where it will go."
                else "Where each platform's downloads land — tap to change:",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FocusedList(platforms) { row, focus ->
                val mapped = destinations.isMapped(row.collection, row.platformKey)
                FocusableRow(onClick = { editing = row }, modifier = focus) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.platformKey, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                destinations.folderFor(row.collection, row.platformKey) +
                                    " · ${row.count} ROMs" + if (mapped) "" else " · no standard folder",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    pendingRoot?.let { newRoot ->
        val oldRoot = destinations.root
        AlertDialog(
            onDismissRequest = { pendingRoot = null },
            title = { Text("Folder changed") },
            text = {
                Text(
                    "Move the files you already downloaded to the new folder, " +
                        "or leave them where they are?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    destinations.rememberRoot(newRoot, GRANT_FLAGS)
                    oldRoot?.let { FileMover.moveAll(placement, queue, destinations, it, newRoot) }
                    pendingRoot = null
                }) { Text("Move files") }
            },
            dismissButton = {
                TextButton(onClick = {
                    destinations.rememberRoot(newRoot, GRANT_FLAGS)
                    pendingRoot = null
                }) { Text("Leave them") }
            },
        )
    }

    pendingFolderMove?.let { move ->
        AlertDialog(
            onDismissRequest = { pendingFolderMove = null },
            title = { Text("Folder changed") },
            text = {
                Text(
                    "Move ${move.items.size} downloaded files from ${move.from} to ${move.to}, " +
                        "or leave them where they are?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    destinations.root?.let {
                        FileMover.moveBetweenFolders(placement, queue, it, move.from, move.to, move.items)
                    }
                    pendingFolderMove = null
                }) { Text("Move files") }
            },
            dismissButton = {
                TextButton(onClick = { pendingFolderMove = null }) { Text("Leave them") }
            },
        )
    }
}

@Composable
private fun FolderEditor(
    destinations: Destinations,
    row: PlatformRow,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
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
                onClick = { onSave(name.trim()) },
            ) {
                Text("Save")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
