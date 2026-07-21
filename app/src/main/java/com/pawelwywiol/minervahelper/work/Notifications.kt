package com.pawelwywiol.minervahelper.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.pawelwywiol.minervahelper.R

internal object Notifications {

    const val CHANNEL_SYNC = "catalog_sync"
    const val CHANNEL_DOWNLOAD = "downloads"
    const val ID_SYNC = 1
    const val ID_DOWNLOAD = 2

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            channel(CHANNEL_SYNC, context.getString(R.string.channel_catalog_sync)),
        )
        manager.createNotificationChannel(
            channel(CHANNEL_DOWNLOAD, context.getString(R.string.channel_downloads)),
        )
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
