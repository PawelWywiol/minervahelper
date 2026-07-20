package com.pawelwywiol.minervahelper.ui.onboarding

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pawelwywiol.minervahelper.data.placement.Destinations
import com.pawelwywiol.minervahelper.data.settings.Setup
import com.pawelwywiol.minervahelper.ui.Screen
import com.pawelwywiol.minervahelper.ui.initialFocus
import com.pawelwywiol.minervahelper.work.CatalogSyncWorker
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.collectAsState

/**
 * Everything that has to happen before the app is usable, in the order it has to happen, with the
 * step the user is on made obvious.
 *
 * Previously these were three things a newcomer had to know about and find: accept nothing, guess
 * that the catalogue is imported from a screen labelled "Dev", and discover separately that a
 * destination folder must be chosen. Each of them silently blocked everything downstream.
 */
@Composable
fun OnboardingScreen(
    setup: Setup,
    destinations: Destinations,
    catalogReady: Boolean,
    onCatalogImported: () -> Unit,
    onFinished: () -> Unit,
) {
    val step = when {
        !setup.accepted -> Step.DISCLAIMER
        destinations.root == null -> Step.FOLDER
        !catalogReady -> Step.CATALOGUE
        else -> Step.DONE
    }

    LaunchedEffect(step) { if (step == Step.DONE) onFinished() }

    Screen("Setup — step ${step.number} of 3") {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            when (step) {
                Step.DISCLAIMER -> Disclaimer(setup::accept)
                Step.FOLDER -> FolderStep(destinations)
                Step.CATALOGUE -> CatalogueStep(onCatalogImported)
                Step.DONE -> Text("Ready.")
            }
        }
    }
}

private enum class Step(val number: Int) {
    DISCLAIMER(1), FOLDER(2), CATALOGUE(3), DONE(3)
}

@Composable
private fun Disclaimer(onAccept: () -> Unit) {
    Text("What this app does", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Minerva Helper finds files in a public archive and downloads the ones you pick onto this " +
            "device. It never sends your files to anyone else.\n\n" +
            "What you download, and whether you are allowed to have it, is your decision and your " +
            "responsibility. Copyright rules differ from country to country. The author of this " +
            "app does not host any of these files, cannot check what you choose, and takes no " +
            "responsibility for it.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onAccept, modifier = Modifier.initialFocus(true)) {
        Text("I understand")
    }
}

@Composable
private fun FolderStep(destinations: Destinations) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            destinations.rememberRoot(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }
    Text("Where should ROMs go?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Pick the folder your emulator reads — usually the one called ROMs. The app gets access to " +
            "that folder only, and creates a subfolder inside it for each console.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = { picker.launch(null) }, modifier = Modifier.initialFocus(true)) {
        Text("Choose folder")
    }
}

@Composable
private fun CatalogueStep(onImported: () -> Unit) {
    val context = LocalContext.current
    val work by remember {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(CatalogSyncWorker.NAME)
            .map { it.firstOrNull() }
    }.collectAsState(initial = null)

    val percent = work?.progress?.getInt(CatalogSyncWorker.KEY_PERCENT, 0) ?: 0
    val stage = work?.progress?.getString(CatalogSyncWorker.KEY_STAGE)
    val running = work?.state == WorkInfo.State.RUNNING || work?.state == WorkInfo.State.ENQUEUED

    // The catalogue is opened once at startup, so a freshly imported one is invisible until
    // something asks for it again.
    LaunchedEffect(work?.state) { if (work?.state == WorkInfo.State.SUCCEEDED) onImported() }

    Text("Getting the list of games", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "The archive's index is downloaded once and turned into a searchable list on this device. " +
            "It is about 1.6 GB and needs Wi-Fi, so it takes a while — you can leave the app while " +
            "it runs.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))

    if (running) {
        Text(
            when (stage) {
                "downloading" -> "Downloading the index — $percent%"
                "importing" -> "Building the list — $percent%"
                else -> "Starting…"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        if (work?.state == WorkInfo.State.FAILED) {
            Text(
                "That did not work. Check the Wi-Fi connection and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { CatalogSyncWorker.enqueue(context) },
            modifier = Modifier.initialFocus(true),
        ) {
            Text(if (work?.state == WorkInfo.State.FAILED) "Try again" else "Start")
        }
    }
}
