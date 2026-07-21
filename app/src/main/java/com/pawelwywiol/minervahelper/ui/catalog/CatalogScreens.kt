package com.pawelwywiol.minervahelper.ui.catalog

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.pawelwywiol.minervahelper.data.catalog.CatalogReader
import com.pawelwywiol.minervahelper.data.catalog.CatalogSync
import com.pawelwywiol.minervahelper.data.metadata.GameInfo
import com.pawelwywiol.minervahelper.data.metadata.GameInfoLookup
import com.pawelwywiol.minervahelper.data.catalog.Favourites
import com.pawelwywiol.minervahelper.data.catalog.Systems
import com.pawelwywiol.minervahelper.data.placement.Destinations
import com.pawelwywiol.minervahelper.data.placement.FilePlacement
import com.pawelwywiol.minervahelper.data.queue.DownloadQueue
import com.pawelwywiol.minervahelper.work.DownloadWorker
import com.pawelwywiol.minervahelper.ui.FocusableRow
import com.pawelwywiol.minervahelper.ui.FocusedList
import com.pawelwywiol.minervahelper.ui.MediaIcon
import com.pawelwywiol.minervahelper.ui.Screen
import com.pawelwywiol.minervahelper.ui.formatCount
import com.pawelwywiol.minervahelper.ui.formatSize
import com.pawelwywiol.minervahelper.ui.initialFocus
import com.pawelwywiol.minervahelper.work.CatalogSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
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
    LaunchedEffect(pager) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { if (it < PREFETCH) pager.loadBefore() }
    }

    val downloadedKeys = remember(queue.revision) { queue.downloadedKeys() }
    Screen(title, showBack = true) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                itemsIndexed(pager.roms, key = { _, rom -> rom.id }) { index, rom ->
                    RomRow(
                        rom = rom,
                        downloadedKeys = downloadedKeys,
                        onClick = { selected = rom.id },
                        selected = rom.id == selected,
                        modifier = Modifier.initialFocus(index == 0 && !pager.trimmedAbove),
                    )
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

    LaunchedEffect(Unit) {
        snapshotFlow { query }.debounce(250).collect { text ->
            hits = withContext(Dispatchers.IO) { reader.search(text) }
        }
    }

    Screen("Search") {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // A down-press must leave the field: the text field consumes arrow keys for cursor
                // movement, which traps a d-pad user with a system keyboard they cannot drive.
                val focusManager = LocalFocusManager.current
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("ROM name") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            } else false
                        },
                )
                Spacer(Modifier.height(8.dp))
                SearchKeypad(
                    onKey = { query += it },
                    onBackspace = { query = query.dropLast(1) },
                )
                Spacer(Modifier.height(8.dp))
                if (hits.isEmpty()) {
                    Text(
                        if (query.isBlank()) "Type a game's name to search the whole archive."
                        else "Nothing found for “$query”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                val downloadedKeys = remember(queue.revision) { queue.downloadedKeys() }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(hits, key = { it.id }) { rom ->
                        RomRow(
                            rom = rom,
                            downloadedKeys = downloadedKeys,
                            onClick = { selected = rom.id },
                            selected = rom.id == selected,
                        )
                    }
                }
            }
            RomDetailPane(reader, favourites, queue, selected, Modifier.weight(1f))
        }
    }
}

private val KEY_ROWS = listOf(
    "abcdefghij".toList(),
    "klmnopqrst".toList(),
    "uvwxyz".toList(),
    "0123456789".toList(),
)

/**
 * The system keyboard cannot be driven by the d-pad at all (measured on the device: LatinIME
 * ignores every dpad and confirm key), so gamepad search needs its own keys.
 */
@Composable
private fun SearchKeypad(onKey: (Char) -> Unit, onBackspace: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        KEY_ROWS.forEachIndexed { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEachIndexed { i, c ->
                    Key(
                        c.toString(),
                        Modifier.weight(1f).initialFocus(rowIndex == 0 && i == 0),
                    ) { onKey(c) }
                }
                if (rowIndex == 2) {
                    Key("space", Modifier.weight(2f)) { onKey(' ') }
                    Key("⌫", Modifier.weight(2f)) { onBackspace() }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(
                if (focused) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
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
            Text(
                "Nothing here yet. Open any game and press “Add to favourites” " +
                    "to keep it at hand.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            return@Screen
        }
        val downloadedKeys = remember(queue.revision) { queue.downloadedKeys() }
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                FocusedList(roms) { rom, focus ->
                    RomRow(
                        rom = rom,
                        downloadedKeys = downloadedKeys,
                        onClick = { selected = rom.id },
                        selected = rom.id == selected,
                        modifier = focus,
                    )
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
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val inQueue = remember(queue.revision, it.id) { queue.contains(it) }
                val downloaded = remember(queue.revision, it.id) { queue.isDownloaded(it) }
                val lookup = remember { GameInfoLookup(context, OkHttpClient()) }
                var info by remember(it.id) { mutableStateOf<GameInfo?>(null) }
                LaunchedEffect(it.id) { info = lookup.lookup(it) }
                Text(it.name, style = MaterialTheme.typography.titleMedium)
                info?.takeUnless { i -> i.isEmpty }?.let { i ->
                    i.title?.let { title ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            title + (i.system?.let { s -> " · $s" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    i.cover?.let { file ->
                        val bitmap = remember(file.path) { BitmapFactory.decodeFile(file.path) }
                        bitmap?.let { b ->
                            Spacer(Modifier.height(8.dp))
                            Image(
                                b.asImageBitmap(),
                                contentDescription = "Cover",
                                modifier = Modifier.height(180.dp),
                            )
                        }
                    }
                }
                if (downloaded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "✓ Downloaded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { favourites.toggle(it) }) {
                    Text(if (favourites.contains(it)) "Remove from favourites" else "Add to favourites")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { scope.launch { toggleDownload(context, queue, it) } }) {
                    Text(
                        when {
                            inQueue -> "Remove from queue"
                            downloaded -> "Download again"
                            else -> "Download"
                        }
                    )
                }
                Text(
                    "Downloads start by themselves, over Wi-Fi, in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/**
 * Queueing is more than an insert: a file already sitting in the destination counts as downloaded
 * instead of being fetched again (R38), and anything genuinely queued starts downloading on its
 * own (R36) — unless the user explicitly asks for a re-download of something they already have.
 */
private suspend fun toggleDownload(
    context: android.content.Context,
    queue: DownloadQueue,
    rom: CatalogReader.Rom,
) {
    val destinations = Destinations.get(context)
    val added = withContext(Dispatchers.IO) {
        when {
            queue.contains(rom) -> { queue.toggle(rom); false }
            queue.isDownloaded(rom) -> queue.toggle(rom)
            else -> {
                val root = destinations.root
                val folder = destinations.folderFor(rom.collection, rom.platformKey)
                if (root != null && FilePlacement(context).exists(root, folder, rom.name, rom.size)) {
                    queue.recordDownloaded(rom, folder)
                    false
                } else {
                    queue.toggle(rom)
                }
            }
        }
    }
    if (added) DownloadWorker.enqueue(context)
}

/**
 * One row shape for every list of ROMs. The medium's icon and the system's readable name are what
 * tell a Mega Drive dump apart from an Amiga one at a glance — the file name rarely does.
 */
@Composable
private fun RomRow(
    rom: CatalogReader.Rom,
    downloadedKeys: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val context = LocalContext.current
    val systems = remember { Systems.get(context) }
    val destinations = remember { Destinations.get(context) }
    val slug = destinations.slugFor(rom.collection, rom.platformKey)
    FocusableRow(
        onClick = onClick,
        selected = selected,
        modifier = modifier,
        leading = { MediaIcon(systems.mediaFor(rom.collection, rom.platformKey, slug)) },
    ) {
        Text(rom.name, style = MaterialTheme.typography.bodyLarge)
        RomSubtitle(
            "${systems.labelFor(rom.platformKey, slug)} · ${formatSize(rom.size)}",
            rom, downloadedKeys,
        )
    }
}

@Composable
private fun RomSubtitle(info: String, rom: CatalogReader.Rom, downloadedKeys: Set<String>) {
    if (DownloadQueue.downloadedKey(rom) in downloadedKeys) {
        Row {
            Text(info, style = MaterialTheme.typography.bodySmall)
            Text(
                " · ✓ downloaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    } else {
        Text(info, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Text(value, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))
}
