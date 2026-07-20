package com.pawelwywiol.minervahelper.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pawelwywiol.minervahelper.data.catalog.CatalogReader
import com.pawelwywiol.minervahelper.data.catalog.Favourites
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

private const val PREFETCH = 10

@Composable
fun CollectionsScreen(
    reader: CatalogReader,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    onFavourites: () -> Unit,
    onDeveloper: () -> Unit,
) {
    var collections by remember { mutableStateOf<List<CatalogReader.Collection>>(emptyList()) }
    LaunchedEffect(reader) { collections = withContext(Dispatchers.IO) { reader.collections() } }

    Screen("Collections") {
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            Button(onClick = onSearch) { Text("Search") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onFavourites) { Text("Favourites") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onDeveloper) { Text("Dev") }
        }
        Spacer(Modifier.height(8.dp))
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

    Screen(collection) {
        FocusedList(platforms) { platform, focus ->
            FocusableRow(onClick = { onOpen(platform) }, modifier = focus) {
                Text(platform.key, style = MaterialTheme.typography.titleMedium)
                Text("${formatCount(platform.fileCount)} files", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun RomsScreen(reader: CatalogReader, platformId: Long, title: String, onOpen: (CatalogReader.Rom) -> Unit) {
    val pager = remember(platformId) { RomPager(reader, platformId) }
    val listState = rememberLazyListState()

    LaunchedEffect(pager) { pager.loadMore() }
    LaunchedEffect(pager) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { if (it >= pager.roms.size - PREFETCH) pager.loadMore() }
    }

    Screen(title) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(pager.roms, key = { _, rom -> rom.id }) { index, rom ->
                FocusableRow(onClick = { onOpen(rom) }, modifier = Modifier.initialFocus(index == 0)) {
                    Text(rom.name, style = MaterialTheme.typography.bodyLarge)
                    Text(formatSize(rom.size), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(reader: CatalogReader, onOpen: (CatalogReader.Rom) -> Unit) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<CatalogReader.Rom>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(Unit) {
        snapshotFlow { query }.debounce(250).collect { text ->
            hits = withContext(Dispatchers.IO) { reader.search(text) }
        }
    }

    Screen("Search") {
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
                FocusableRow(onClick = { onOpen(rom) }) {
                    Text(rom.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${rom.collection}/${rom.platformKey} · ${formatSize(rom.size)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun FavouritesScreen(
    reader: CatalogReader,
    favourites: Favourites,
    onOpen: (CatalogReader.Rom) -> Unit,
) {
    val keys = favourites.all()
    var roms by remember { mutableStateOf<List<CatalogReader.Rom>>(emptyList()) }
    LaunchedEffect(keys) { roms = withContext(Dispatchers.IO) { keys.mapNotNull(reader::rom) } }

    Screen("Favourites") {
        if (keys.isEmpty()) {
            Text("Nothing here yet.", modifier = Modifier.padding(horizontal = 16.dp))
            return@Screen
        }
        FocusedList(roms) { rom, focus ->
            FocusableRow(onClick = { onOpen(rom) }, modifier = focus) {
                Text(rom.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${rom.collection}/${rom.platformKey} · ${formatSize(rom.size)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun RomDetailScreen(reader: CatalogReader, favourites: Favourites, romId: Long) {
    var rom by remember { mutableStateOf<CatalogReader.Rom?>(null) }
    LaunchedEffect(romId) { rom = withContext(Dispatchers.IO) { reader.rom(romId) } }

    Screen(rom?.name ?: "…") {
        rom?.let {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Button(
                    onClick = { favourites.toggle(it) },
                    modifier = Modifier.initialFocus(true),
                ) {
                    Text(if (favourites.contains(it)) "Remove from favourites" else "Add to favourites")
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

@Composable
private fun Screen(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
}

@Composable
private fun <T> FocusedList(items: List<T>, row: @Composable (T, Modifier) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items) { index, item -> row(item, Modifier.initialFocus(index == 0)) }
    }
}

/**
 * The requester has to sit on the same node as the row's own focus target, and the request has to
 * come from inside that row's composition — asking from the list before its first item exists fails
 * silently and leaves the screen unreachable by gamepad.
 */
@Composable
private fun Modifier.initialFocus(enabled: Boolean): Modifier {
    if (!enabled) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    return focusRequester(requester)
}

/**
 * The handheld has no pointer, so the focused row is the only cue about what the action button will
 * open — it has to be visible without looking for it.
 */
@Composable
private fun FocusableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        content = content,
    )
}
