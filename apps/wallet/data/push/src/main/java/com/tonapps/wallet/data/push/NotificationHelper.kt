package com.tonapps.wallet.data.push

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tonapps.security.Security

internal class NotificationHelper(context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    fun getChannel(id: String): NotificationChannelCompat {
        return notificationManager.getNotificationChannelCompat(id) ?: createChannel(id)
    }

    private fun createChannel(id: String): NotificationChannelCompat {
        val builder = NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        builder.setName(id)
        val channel = builder.build()
        notificationManager.createNotificationChannel(channel)
        return channel
    }

    fun createPendingIntent(context: Context, intent: Intent): PendingIntent {
        intent.`package` = context.packageName
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun newId(): Int {
        return Security.secureRandom().nextInt()
    }

    fun baseBuilder(
        context: Context,
        channelId: String
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channelId)
        builder.setSmallIcon(R.drawable.ic_push)
        builder.setColorized(true)
        builder.setGroupSummary(true)
        builder.setShowWhen(true)
        return builder
    }

    fun find(predicate: (Notification) -> Boolean): StatusBarNotification? {
        val activeNotifications = notificationManager.activeNotifications
        return activeNotifications.firstOrNull { predicate(it.notification) }
    }

    fun findIdOrNew(predicate: (Notification) -> Boolean): Int {
        return find { predicate(it) }?.id ?: newId()
    }

    @SuppressLint("MissingPermission")
    fun display(id: Int, notification: Notification) {
        notificationManager.notify(id, notification)
    }

}