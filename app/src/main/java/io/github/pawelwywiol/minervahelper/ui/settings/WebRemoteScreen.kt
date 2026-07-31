package io.github.pawelwywiol.minervahelper.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.pawelwywiol.minervahelper.data.web.Pairing
import io.github.pawelwywiol.minervahelper.data.web.WebRemote
import io.github.pawelwywiol.minervahelper.ui.Screen
import io.github.pawelwywiol.minervahelper.ui.initialFocus
import kotlinx.coroutines.delay

/**
 * The browser remote's own screen rather than a block inside Settings: the address, a pairing code
 * and the list of browsers already let in do not fit under the folder settings on a 4.7" display,
 * and this is not something to operate half-visible.
 *
 * Off is the default and staying off has to be the easy state — a port left open and forgotten is
 * the only real risk this feature adds.
 */
@Composable
fun WebRemoteScreen() {
    val context = LocalContext.current
    val pairing = remember { Pairing.get(context) }
    var confirming by remember { mutableStateOf<String?>(null) }

    // The running server's only outward sign is its notification, and on Android 13 and later that
    // needs a permission this app has never asked for. Asked at the moment it matters; the server
    // starts either way, because refusing to serve over a missing notification would be theatre —
    // but then the screen says plainly that nothing will show up in the shade.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { WebRemote.start(context) }

    fun turnOn() {
        val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsking) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else WebRemote.start(context)
    }

    Screen("Browser remote", showBack = true) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Pick games from a browser on a computer or phone connected to the same Wi-Fi. " +
                    "The downloading still happens here, on this device, and downloaded files are " +
                    "never sent to the browser.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (WebRemote.running) {
                OutlinedButton(
                    onClick = {
                        pairing.withdrawOffer()
                        WebRemote.stop(context)
                    },
                    modifier = Modifier.initialFocus(true),
                ) { Text("Turn off") }
            } else {
                Button(
                    onClick = ::turnOn,
                    modifier = Modifier.initialFocus(true),
                ) { Text("Turn on") }
            }

            WebRemote.problem?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            WebRemote.url?.let { url ->
                val silent = remember(WebRemote.running) {
                    !NotificationManagerCompat.from(context).areNotificationsEnabled()
                }
                if (silent) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Notifications are switched off for this app, so the remote will not show " +
                            "one while it runs — this screen is the only place that says it is on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Open this in a browser", style = MaterialTheme.typography.titleSmall)
                Text(url, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                PairingCode(pairing)
            }

            if (pairing.clients.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Paired browsers", style = MaterialTheme.typography.titleSmall)
                Text(
                    "A paired browser stays paired until you unpair it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pairing.clients.forEach { client ->
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            client.agent,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        // Unpairing removes the row, so the list closes up under the finger and a
                        // second tap in the same place would hit whoever moved into it. Asking first
                        // costs one press and makes that impossible.
                        if (confirming == client.id) {
                            TextButton(onClick = {
                                pairing.revoke(client.id)
                                confirming = null
                            }) { Text("Unpair for good") }
                            TextButton(onClick = { confirming = null }) { Text("Keep") }
                        } else {
                            TextButton(onClick = { confirming = client.id }) { Text("Unpair") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PairingCode(pairing: Pairing) {
    // The code has to look like it is running out, or a stale one on screen reads as a broken app.
    // The loop also withdraws the lapsed code, so nothing on screen outlives what the server accepts.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(pairing.offer) {
        if (pairing.offer == null) return@LaunchedEffect
        while (pairing.secondsLeft() > 0) {
            delay(1000)
            tick++
        }
        pairing.withdrawOffer()
    }
    val seconds = remember(tick, pairing.offer) { pairing.secondsLeft() }
    val offer = pairing.offer

    if (offer == null || seconds == 0) {
        Button(onClick = { pairing.offerPin() }) { Text("Show pairing code") }
        return
    }
    Text(offer.pin, style = MaterialTheme.typography.displaySmall)
    Text(
        "Type this into the browser — expires in ${seconds}s",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
