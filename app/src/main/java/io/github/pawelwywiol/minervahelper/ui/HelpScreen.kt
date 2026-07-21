package io.github.pawelwywiol.minervahelper.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Topic(val title: String, val body: String)

private val TOPICS = listOf(
    Topic(
        "What this app is for",
        "It finds retro games in a large public archive and downloads only the ones you pick, " +
            "straight onto this device. No computer, no cables, no torrent client.",
    ),
    Topic(
        "Finding a game",
        "Browse walks through the archive: collection, then system, then games. Pressing Browse " +
            "again goes one step back up.\n\n" +
            "Search looks through everything by name and answers as you type. It has its own " +
            "keys on screen, so it works with the buttons alone.",
    ),
    Topic(
        "Downloading",
        "Open a game and press Download. That is the whole thing — there is no second button to " +
            "find. Downloading starts on its own, in the background, and only over Wi-Fi.\n\n" +
            "You can close the app, or even restart the device. Whatever was unfinished carries on.",
    ),
    Topic(
        "The queue",
        "Queue shows what is waiting and what is arriving, with speed and free space.\n\n" +
            "The order is the download order. Drag a torrent by its handle, or use its arrows, to " +
            "decide what arrives first. Pause one game and the rest keep going.\n\n" +
            "Finished games leave the queue, and lists mark what you already have.",
    ),
    Topic(
        "Where games are saved",
        "Each system has its own folder, so your emulator front-end finds the games where it " +
            "already looks for them. Settings shows every folder and lets you change any of them.\n\n" +
            "If you move a folder later, the app offers to bring the files you already have along.",
    ),
    Topic(
        "If something goes wrong",
        "A failed game says why on its row. \"No one is sharing this file right now\" means the " +
            "archive has no one online holding it — that is not something the app can fix, so try " +
            "again later. Retry failed puts everything that failed back in the queue.",
    ),
    Topic(
        "Making it easier to read",
        "Settings has a text size that everything in the app follows, from a fifth smaller up to " +
            "twice the size.",
    ),
    Topic(
        "Your files stay yours",
        "The app never shares what you download. Ordinary torrent programs upload to other people " +
            "while and after they fetch a file; this one cannot, by the way it is built.",
    ),
)

@Composable
internal fun HelpScreen() {
    Screen("How to use this") {
        Text(
            "The short version: find a game, press Download, and it appears in your emulator.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(TOPICS) { topic ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusable()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(topic.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        topic.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
