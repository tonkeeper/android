package com.tonapps.tonkeeper.extensions

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews

fun RemoteViews.setOnClickIntent(context: Context, id: Int, intent: Intent) {
    val flags = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (intent.component != null || intent.hasCategory(Intent.CATEGORY_BROWSABLE)) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
        }
        else -> PendingIntent.FLAG_UPDATE_CURRENT
    }

    val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
    setOnClickPendingIntent(id, pendingIntent)
}

fun RemoteViews.setOnClickIntent(context: Context, id: Int, uri: Uri) {
    setOnClickIntent(context, id, Intent(Intent.ACTION_VIEW, uri))
}