package com.tonapps.tonkeeper.helper

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import com.tonapps.wallet.localization.Localization

object NotificationsHelper {

    fun getPendingIntent(context: Context, url: String): PendingIntent {
        return getPendingIntent(context, url.toUri())
    }

    fun getPendingIntent(context: Context, uri: Uri): PendingIntent {
        val intent = Intent(context, RootActivity::class.java)
        intent.data = uri
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun getOrCreateChannel(context: Context, id: String): NotificationChannelCompat {
        val manager = createNotificationManager(context)
        val channel = manager.getNotificationChannelCompat(id)
        if (channel == null) {
            val builder = NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_LOW)
            builder.setName(context.getString(Localization.notification_channel_updates_title))
            builder.setDescription(context.getString(Localization.notification_channel_updates_description))
            val newChannel = builder.build()
            manager.createNotificationChannel(newChannel)
            return newChannel
        }
        return channel
    }

    fun createNotificationManager(context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }
}