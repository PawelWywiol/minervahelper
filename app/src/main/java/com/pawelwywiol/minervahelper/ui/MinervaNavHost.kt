package com.pawelwywiol.minervahelper.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pawelwywiol.minervahelper.data.catalog.CatalogReader
import com.pawelwywiol.minervahelper.data.catalog.Favourites
import com.pawelwywiol.minervahelper.data.queue.DownloadQueue
import com.pawelwywiol.minervahelper.ui.catalog.CollectionsScreen
import com.pawelwywiol.minervahelper.ui.catalog.FavouritesScreen
import com.pawelwywiol.minervahelper.ui.catalog.PlatformsScreen
import com.pawelwywiol.minervahelper.ui.catalog.RomDetailScreen
import com.pawelwywiol.minervahelper.ui.catalog.RomsScreen
import com.pawelwywiol.minervahelper.ui.catalog.SearchScreen
import com.pawelwywiol.minervahelper.ui.queue.QueueScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MinervaNavHost() {
    val navController = rememberNavController()
    val catalog = rememberCatalog()
    val context = LocalContext.current
    val favourites = remember { Favourites(context) }
    val queue = remember { DownloadQueue(context) }

    NavHost(navController, startDestination = "collections") {
        composable("dev") { SpikeScreen() }
        composable("collections") {
            WithCatalog(catalog, navController::navigate) { catalog ->
                CollectionsScreen(
                    reader = catalog,
                    onOpen = { navController.navigate("platforms/${Uri.encode(it)}") },
                    onSearch = { navController.navigate("search") },
                    onFavourites = { navController.navigate("favourites") },
                    onQueue = { navController.navigate("queue") },
                    onDeveloper = { navController.navigate("dev") },
                )
            }
        }
        composable("platforms/{collection}") { entry ->
            val collection = Uri.decode(entry.arguments?.getString("collection").orEmpty())
            WithCatalog(catalog, navController::navigate) { catalog ->
                PlatformsScreen(catalog, collection) {
                    navController.navigate("roms/${it.id}/${Uri.encode(it.key)}")
                }
            }
        }
        composable("roms/{platformId}/{title}") { entry ->
            val platformId = entry.arguments?.getString("platformId")?.toLongOrNull() ?: 0L
            val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
            WithCatalog(catalog, navController::navigate) { catalog ->
                RomsScreen(catalog, platformId, title) { navController.navigate("rom/${it.id}") }
            }
        }
        composable("search") {
            WithCatalog(catalog, navController::navigate) { catalog ->
                SearchScreen(catalog) { navController.navigate("rom/${it.id}") }
            }
        }
        composable("queue") { QueueScreen(queue) }
        composable("favourites") {
            WithCatalog(catalog, navController::navigate) { reader ->
                FavouritesScreen(reader, favourites) { navController.navigate("rom/${it.id}") }
            }
        }
        composable("rom/{romId}") { entry ->
            val romId = entry.arguments?.getString("romId")?.toLongOrNull() ?: 0L
            WithCatalog(catalog, navController::navigate) { reader ->
                RomDetailScreen(reader, favourites, queue, romId)
            }
        }
    }
}

/**
 * Opening the catalog is asynchronous, so "not loaded yet" must be a distinct state from "not
 * there" — collapsing the two sends every cold start to the import screen even when the catalog
 * is present.
 */
private sealed interface CatalogState {
    data object Loading : CatalogState
    data object Missing : CatalogState
    data class Ready(val reader: CatalogReader) : CatalogState
}

@Composable
private fun WithCatalog(
    state: CatalogState,
    navigate: (String) -> Unit,
    content: @Composable (CatalogReader) -> Unit,
) = when (state) {
    is CatalogState.Ready -> content(state.reader)
    CatalogState.Loading -> Message("Opening the catalogue…")
    CatalogState.Missing -> {
        Message("No catalogue yet. Import it from the Developer screen.")
        LaunchedEffect(Unit) { navigate("dev") }
    }
}

@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) { Text(text) }
}

@Composable
private fun rememberCatalog(): CatalogState {
    val context = LocalContext.current
    var state by remember { mutableStateOf<CatalogState>(CatalogState.Loading) }

    LaunchedEffect(Unit) {
        state = withContext(Dispatchers.IO) {
            runCatching {
                val opened = CatalogReader(File(context.filesDir, "catalog.db"))
                if (opened.isComplete()) CatalogState.Ready(opened) else null.also { opened.close() }
            }.getOrNull() ?: CatalogState.Missing
        }
    }
    DisposableEffect(Unit) { onDispose { (state as? CatalogState.Ready)?.reader?.close() } }
    return state
}
