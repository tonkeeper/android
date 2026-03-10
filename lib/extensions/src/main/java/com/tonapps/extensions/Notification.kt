package com.tonapps.extensions

import android.app.Notification

fun Notification.getContentText(): String? {
    return extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
}