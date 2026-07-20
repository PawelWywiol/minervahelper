package com.pawelwywiol.minervahelper.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.pawelwywiol.minervahelper.R

internal object Notifications {

    const val CHANNEL_SYNC = "catalog_sync"
    const val ID_SYNC = 1
    const val ID_DOWNLOAD = 2

    fun ensureChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_SYNC,
            context.getString(R.string.channel_catalog_sync),
            NotificationManager.IMPORTANCE_LOW,
        )
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    fun progress(context: Context, text: String, percent: Int?): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.sync_title))
            .setContentText(text)
            .setOngoing(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
}
