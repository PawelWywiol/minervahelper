package com.pawelwywiol.minervahelper.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pawelwywiol.minervahelper.data.catalog.CatalogReader
import com.pawelwywiol.minervahelper.data.catalog.CatalogSync
import com.pawelwywiol.minervahelper.data.catalog.Favourites
import com.pawelwywiol.minervahelper.data.queue.DownloadQueue
import com.pawelwywiol.minervahelper.ui.FocusableRow
import com.pawelwywiol.minervahelper.ui.FocusedList
import com.pawelwywiol.minervahelper.ui.Screen
import com.pawelwywiol.minervahelper.ui.formatCount
import com.pawelwywiol.minervahelper.ui.formatSize
import com.pawelwywiol.minervahelper.ui.initialFocus
import com.pawelwywiol.minervahelper.work.CatalogSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

private const val PREFETCH = 10

@Composable
fun CollectionsScreen(reader: CatalogReader, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var collections by remember { mutableStateOf<List<CatalogReader.Collection>>(emptyList()) }
    LaunchedEffect(reader) { collections = withContext(Dispatchers.IO) { reader.collections() } }

    // One HEAD request per visit to this screen. Checked here rather than at startup so a slow or
    // absent network delays nothing the user is waiting for.
    var updateAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(reader) {
        updateAvailable = runCatching {
            CatalogSync(context, OkHttpClient()).hasUpdate()
        }.getOrDefault(false)
    }

    Screen("Collections") {
        if (updateAvailable) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "The archive has published a newer list of games.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    CatalogSyncWorker.enqueue(context, force = true)
                    updateAvailable = false
                }) {
                    Text("Update the list")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        FocusedList(collections) { collection, focus ->
            FocusableRow(onClick = { onOpen(collection.name) }, modifier = focus) {
                Text(collection.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatCount(collection.fileCount)} files · ${collection.platformCount} platforms",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun PlatformsScreen(reader: CatalogReader, collection: String, onOpen: (CatalogReader.Platform) -> Unit) {
    var platforms by remember { mutableStateOf<List<CatalogReader.Platform>>(emptyList()) }
    LaunchedEffect(collection) { platforms = withContext(Dispatchers.IO) { reader.platforms(collection) } }

    Screen(collection, showBack = true) {
        FocusedList(platforms) { platform, focus ->
            FocusableRow(onClick = { onOpen(platform) }, modifier = focus) {
                Text(platform.key, style = MaterialTheme.typography.titleMedium)
                Text("${formatCount(platform.fileCount)} files", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun RomsScreen(
    reader: CatalogReader,
    favourites: Favourites,
    queue: DownloadQueue,
    platformId: Long,
    title: String,
) {
    val pager = remember(platformId) { RomPager(reader, platformId) }
    val listState = rememberLazyListState()
    var selected by remember(platformId) { mutableStateOf<Long?>(null) }

    LaunchedEffect(pager) { pager.loadMore() }
    LaunchedEffect(pager) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { if (it >= pager.roms.size - PREFETCH) pager.loadMore() }
    }

    Screen(title, showBack = true) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                itemsIndexed(pager.roms, key = { _, rom -> rom.id }) { index, rom ->
                    FocusableRow(
                        onClick = { selected = rom.id },
                        selected = rom.id == selected,
                        modifier = Modifier.initialFocus(index == 0),
                    ) {
                        Text(rom.name, style = MaterialTheme.typography.bodyLarge)
                        Text(formatSize(rom.size), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            RomDetailPane(reader, favourites, queue, selected, Modifier.weight(1f))
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(reader: CatalogReader, favourites: Favourites, queue: DownloadQueue) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<CatalogReader.Rom>>(emptyList()) }
    var selected by remember { mutableStateOf<Long?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(Unit) {
        snapshotFlow { query }.debounce(250).collect { text ->
            hits = withContext(Dispatchers.IO) { reader.search(text) }
        }
    }

    Screen("Search") {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("ROM name") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focusRequester),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(hits, key = { it.id }) { rom ->
                        FocusableRow(onClick = { selected = rom.id }, selected = rom.id == selected) {
                            Text(rom.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${rom.collection}/${rom.platformKey} · ${formatSize(rom.size)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            RomDetailPane(reader, favourites, queue, selected, Modifier.weight(1f))
        }
    }
}

@Composable
fun FavouritesScreen(reader: CatalogReader, favourites: Favourites, queue: DownloadQueue) {
    val keys = favourites.all()
    var roms by remember { mutableStateOf<List<CatalogReader.Rom>>(emptyList()) }
    var selected by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(keys) { roms = withContext(Dispatchers.IO) { keys.mapNotNull(reader::rom) } }

    Screen("Favourites") {
        if (keys.isEmpty()) {
            Text("Nothing here yet.", modifier = Modifier.padding(horizontal = 16.dp))
            return@Screen
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                FocusedList(roms) { rom, focus ->
                    FocusableRow(
                        onClick = { selected = rom.id },
                        selected = rom.id == selected,
                        modifier = focus,
                    ) {
                        Text(rom.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${rom.collection}/${rom.platformKey} · ${formatSize(rom.size)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            RomDetailPane(reader, favourites, queue, selected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RomDetailPane(
    reader: CatalogReader,
    favourites: Favourites,
    queue: DownloadQueue,
    romId: Long?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (romId == null) {
            Text(
                "Tap a game to see its details.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            var rom by remember(romId) { mutableStateOf<CatalogReader.Rom?>(null) }
            LaunchedEffect(romId) { rom = withContext(Dispatchers.IO) { reader.rom(romId) } }
            rom?.let {
                val inQueue = remember(queue.revision, it.id) { queue.contains(it) }
                Text(it.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { favourites.toggle(it) }) {
                    Text(if (favourites.contains(it)) "Remove from favourites" else "Add to favourites")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { queue.toggle(it) }) {
                    Text(if (inQueue) "Remove from queue" else "Add to queue")
                }
                Spacer(Modifier.height(16.dp))
                Detail("Platform", "${it.collection}/${it.platformKey}")
                Detail("Size", formatSize(it.size))
                Detail("Torrent", it.torrentPath)
                Detail("MD5", it.md5 ?: "—")
                Detail("SHA1", it.sha1 ?: "—")
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Text(value, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))
}
