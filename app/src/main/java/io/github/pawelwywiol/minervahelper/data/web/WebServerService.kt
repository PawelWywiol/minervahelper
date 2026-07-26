package io.github.pawelwywiol.minervahelper.data.web

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import io.github.pawelwywiol.minervahelper.R
import io.github.pawelwywiol.minervahelper.work.Notifications
import java.net.Inet4Address

private const val TAG = "MinervaWeb"

/**
 * Keeps the browser remote alive while the app is not on screen, and — more importantly — keeps it
 * visible: a foreground service must show a notification, so the server can never be listening
 * without the owner being able to see that it is.
 *
 * The type is `specialUse` rather than `dataSync`: from Android 15 a dataSync service is stopped
 * after six hours in any 24, which is fine for a download but wrong for something the owner
 * switched on deliberately.
 */
class WebServerService : Service() {

    private val server by lazy { WebServer(applicationContext, Pairing.get(applicationContext)) }
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        Notifications.ensureChannels(applicationContext)
        // startForeground has seconds to happen after startForegroundService, so it goes first and
        // the real address is filled in once the socket is bound.
        foreground(getString(R.string.web_starting))

        val host = wifiAddress()
        if (host == null) {
            WebRemote.problem = "Connect to Wi-Fi first — the remote is never offered over mobile data."
            stopSelf()
            return START_NOT_STICKY
        }
        return try {
            val port = server.start(host)
            WebRemote.url = "http://$host:$port"
            foreground(WebRemote.url.orEmpty())
            watchNetwork()
            START_NOT_STICKY
        } catch (error: Exception) {
            Log.e(TAG, "cannot start server", error)
            WebRemote.problem = "Could not start the server — ${error.message.orEmpty().ifEmpty { "try again" }}"
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        networkCallback?.let { callback ->
            runCatching { getSystemService<ConnectivityManager>()?.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        server.stop()
        WebRemote.url = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun foreground(text: String) = ServiceCompat.startForeground(
        this,
        Notifications.ID_WEB,
        Notifications.webServer(applicationContext, text),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        },
    )

    /**
     * The address is only valid for as long as this Wi-Fi is. Rather than serve on an address that
     * has moved, or keep a socket open on a network the owner did not intend, the server stops and
     * the owner switches it back on.
     */
    private fun watchNetwork() {
        val manager = getSystemService<ConnectivityManager>() ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) = stopBecauseOfNetwork()

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) stopBecauseOfNetwork()
            }
        }
        networkCallback = callback
        manager.registerDefaultNetworkCallback(callback)
    }

    /** Says why, because a remote that vanished on its own otherwise reads as a fault. */
    private fun stopBecauseOfNetwork() {
        WebRemote.problem = "Wi-Fi went away, so the remote turned itself off. Turn it back on " +
            "once you are connected — the address may have changed."
        stopSelf()
    }

    /** Null unless the device is on Wi-Fi: the remote is a home-network tool and nothing else. */
    private fun wifiAddress(): String? {
        val manager = getSystemService<ConnectivityManager>() ?: return null
        val network = manager.activeNetwork ?: return null
        val caps = manager.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        return manager.getLinkProperties(network)?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    companion object {
        const val ACTION_STOP = "io.github.pawelwywiol.minervahelper.STOP_WEB"
    }
}
