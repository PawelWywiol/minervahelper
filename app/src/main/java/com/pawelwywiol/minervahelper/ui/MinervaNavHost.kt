package com.pawelwywiol.minervahelper.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pawelwywiol.minervahelper.BuildConfig
import com.pawelwywiol.minervahelper.data.catalog.CatalogReader
import com.pawelwywiol.minervahelper.data.catalog.Favourites
import com.pawelwywiol.minervahelper.data.placement.Destinations
import com.pawelwywiol.minervahelper.data.settings.Setup
import com.pawelwywiol.minervahelper.data.queue.DownloadQueue
import com.pawelwywiol.minervahelper.data.queue.DownloadState
import com.pawelwywiol.minervahelper.work.DownloadWorker
import com.pawelwywiol.minervahelper.ui.catalog.CollectionsScreen
import com.pawelwywiol.minervahelper.ui.catalog.FavouritesScreen
import com.pawelwywiol.minervahelper.ui.catalog.PlatformsScreen
import com.pawelwywiol.minervahelper.ui.catalog.RomsScreen
import com.pawelwywiol.minervahelper.ui.catalog.SearchScreen
import com.pawelwywiol.minervahelper.ui.onboarding.OnboardingScreen
import com.pawelwywiol.minervahelper.ui.queue.QueueScreen
import com.pawelwywiol.minervahelper.ui.settings.FoldersScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("collections", "Browse", Icons.Filled.Home),
    Tab("search", "Search", Icons.Filled.Search),
    Tab("favourites", "Favourites", Icons.Filled.Favorite),
    Tab("queue", "Queue", Icons.AutoMirrored.Filled.List),
    Tab("folders", "Settings", Icons.Filled.Settings),
)

private val BROWSE_ROUTES = setOf("collections", "platforms/{collection}", "roms/{platformId}/{title}")

@Composable
fun MinervaNavHost() {
    val navController = rememberNavController()
    val (catalog, reloadCatalog) = rememberCatalog()
    val context = LocalContext.current
    val favourites = remember { Favourites(context) }
    val queue = remember { DownloadQueue.get(context) }
    val destinations = remember { Destinations.get(context) }
    val setup = remember { Setup.get(context) }

    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // The queue looks after itself (R36): anything still waiting when the app opens is picked up
    // without the user having to find a button.
    LaunchedEffect(Unit) {
        val waiting = withContext(Dispatchers.IO) {
            queue.all().any { it.state == DownloadState.QUEUED }
        }
        if (waiting && destinations.root != null) DownloadWorker.enqueue(context)
    }

    Scaffold(
        bottomBar = {
            if (route != null && route != "onboarding") {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = if (tab.route == "collections") route in BROWSE_ROUTES
                            else route == tab.route,
                            onClick = {
                                // Browse steps one level up rather than jumping to the top: from a
                                // ROM list it lands on the platforms of that collection, and only
                                // the next press reaches the collections themselves. Wiping the
                                // whole stack lost the level the user was working in.
                                if (tab.route == "collections") {
                                    if (route in BROWSE_ROUTES && route != "collections") {
                                        navController.popBackStack()
                                    } else {
                                        navController.navigate("collections") {
                                            popUpTo("collections") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                } else {
                                    navController.navigate(tab.route) {
                                        popUpTo("collections") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = "collections",
            modifier = Modifier.padding(padding),
        ) {
            composable("dev") { SpikeScreen() }
            composable("onboarding") {
                OnboardingScreen(
                    setup = setup,
                    destinations = destinations,
                    catalogReady = catalog is CatalogState.Ready,
                    onCatalogImported = reloadCatalog,
                    onFinished = {
                        navController.navigate("collections") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                )
            }
            composable("collections") {
                // Setup is incomplete in more ways than a missing catalogue: an app that browses fine
                // but has nowhere to save is a dead end discovered only at the download button.
                val ready = setup.accepted && destinations.root != null
                if (!ready) {
                    Message("Setting up…")
                    LaunchedEffect(Unit) {
                        navController.navigate("onboarding") {
                            popUpTo("collections") { inclusive = true }
                        }
                    }
                    return@composable
                }
                WithCatalog(catalog, navController::navigate) { catalog ->
                    CollectionsScreen(catalog) { navController.navigate("platforms/${Uri.encode(it)}") }
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
                    RomsScreen(catalog, favourites, queue, platformId, title)
                }
            }
            composable("search") {
                WithCatalog(catalog, navController::navigate) { catalog ->
                    SearchScreen(catalog, favourites, queue)
                }
            }
            composable("queue") {
                QueueScreen(queue, destinations) { navController.navigate("folders") }
            }
            composable("folders") {
                FoldersScreen(
                    destinations,
                    queue,
                    onDev = if (BuildConfig.DEBUG) ({ navController.navigate("dev") }) else null,
                )
            }
            composable("favourites") {
                WithCatalog(catalog, navController::navigate) { reader ->
                    FavouritesScreen(reader, favourites, queue)
                }
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
        Message("Setting up…")
        LaunchedEffect(Unit) { navigate("onboarding") }
    }
}

@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) { Text(text) }
}

@Composable
private fun rememberCatalog(): Pair<CatalogState, () -> Unit> {
    val context = LocalContext.current
    var state by remember { mutableStateOf<CatalogState>(CatalogState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        (state as? CatalogState.Ready)?.reader?.close()
        state = withContext(Dispatchers.IO) {
            runCatching {
                val opened = CatalogReader(File(context.filesDir, "catalog.db"))
                if (opened.isComplete()) CatalogState.Ready(opened) else null.also { opened.close() }
            }.getOrNull() ?: CatalogState.Missing
        }
    }
    DisposableEffect(Unit) { onDispose { (state as? CatalogState.Ready)?.reader?.close() } }
    return state to { attempt++ }
}
