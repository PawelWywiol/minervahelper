package com.pawelwywiol.minervahelper.ui

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import com.pawelwywiol.minervahelper.spike.CatalogSpike
import com.pawelwywiol.minervahelper.spike.TorrentSpike
import com.pawelwywiol.minervahelper.work.CatalogSyncWorker
import kotlinx.coroutines.launch
import java.io.File

private const val SPIKE_TAG = "MinervaSpike"

class MainActivity : ComponentActivity() {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        super.dispatchKeyEvent(event.remapGamepad())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpikeScreen()
                }
            }
        }
    }
}

@Composable
private fun SpikeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    var probeCount by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            modifier = Modifier.focusRequester(focusRequester),
            onClick = { probeCount++ },
        ) {
            Text("Gamepad probe — activated $probeCount×")
        }

        Button(
            enabled = !running,
            onClick = {
                running = true
                lines.clear()
                scope.launch {
                    val workDir = File(context.filesDir, "spike").apply { mkdirs() }
                    runCatching {
                        TorrentSpike.run(workDir) { line ->
                            Log.i(SPIKE_TAG, line)
                            lines += line
                        }
                    }.onFailure {
                        Log.e(SPIKE_TAG, "SPIKE FAILED", it)
                        lines += "SPIKE FAILED: ${it::class.simpleName}: ${it.message}"
                    }
                    running = false
                }
            }
        ) {
            Text(if (running) "Running…" else "Run M0 torrent spike")
        }

        Button(
            enabled = !running,
            onClick = {
                running = true
                lines.clear()
                scope.launch {
                    runCatching {
                        CatalogSpike.run(context) { line ->
                            Log.i(SPIKE_TAG, line)
                            lines += line
                        }
                    }.onFailure {
                        Log.e(SPIKE_TAG, "CATALOG SPIKE FAILED", it)
                        lines += "FAILED: ${it::class.simpleName}: ${it.message}"
                    }
                    running = false
                }
            }
        ) {
            Text("Run M1 catalog import")
        }

        Button(onClick = { CatalogSyncWorker.enqueue(context) }) {
            Text("Enqueue M1 sync worker")
        }

        LazyColumn(state = listState, modifier = Modifier.padding(top = 16.dp)) {
            items(lines) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
