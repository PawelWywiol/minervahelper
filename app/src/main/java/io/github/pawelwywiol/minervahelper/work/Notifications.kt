package io.github.pawelwywiol.minervahelper.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import io.github.pawelwywiol.minervahelper.R
import io.github.pawelwywiol.minervahelper.data.web.WebServerService
import io.github.pawelwywiol.minervahelper.ui.MainActivity

internal object Notifications {

    const val CHANNEL_SYNC = "catalog_sync"
    const val CHANNEL_DOWNLOAD = "downloads"
    const val CHANNEL_WEB = "web_remote"
    const val ID_SYNC = 1
    const val ID_DOWNLOAD = 2
    const val ID_WEB = 3

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            channel(CHANNEL_SYNC, context.getString(R.string.channel_catalog_sync)),
        )
        manager.createNotificationChannel(
            channel(CHANNEL_DOWNLOAD, context.getString(R.string.channel_downloads)),
        )
        manager.createNotificationChannel(
            channel(CHANNEL_WEB, context.getString(R.string.channel_web_remote)),
        )
    }

    /**
     * Not merely progress: this notification is the only thing that makes an open port visible, so it
     * shows the address and carries the switch that closes it.
     */
    fun webServer(context: Context, text: String): android.app.Notification {
        val stop = Intent(context, WebServerService::class.java).setAction(WebServerService.ACTION_STOP)
        return NotificationCompat.Builder(context, CHANNEL_WEB)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.web_title))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .addAction(
                0,
                context.getString(R.string.web_stop),
                PendingIntent.getService(
                    context, 1, stop,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
    }

    fun syncProgress(context: Context, text: String, percent: Int?): android.app.Notification =
        progress(context, CHANNEL_SYNC, R.string.sync_title, text, percent)

    fun downloadProgress(context: Context, text: String, percent: Int?): android.app.Notification =
        progress(context, CHANNEL_DOWNLOAD, R.string.download_title, text, percent)

    private fun channel(id: String, name: String) =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)

    private fun progress(
        context: Context,
        channelId: String,
        @StringRes titleRes: Int,
        text: String,
        percent: Int?,
    ): android.app.Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(titleRes))
            .setContentText(text)
            .setOngoing(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
}
