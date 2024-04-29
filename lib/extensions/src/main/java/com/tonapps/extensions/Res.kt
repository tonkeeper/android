package com.tonapps.extensions

import android.net.Uri

fun Int.uri(): Uri {
    return Uri.Builder().scheme("res").path(toString()).build()
}
