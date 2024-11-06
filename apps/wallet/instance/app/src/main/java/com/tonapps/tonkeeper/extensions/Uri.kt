package com.tonapps.tonkeeper.extensions

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.ColorInt
import com.tonapps.extensions.isLocal
import uikit.extensions.drawable

fun Uri.isTonSite(): Boolean {
    return this.host?.endsWith(".ton") ?: false
}

fun Uri.normalizeTONSites(): Uri {
    if (!isTonSite()) {
        return this
    }
    val proxyServerHost = "magic.org"
    val host = this.host ?: ""
    val fixedHost = host.replace("-", "-h").replace(".", "-d")

    val path = this.path ?: ""
    val fixedPath = if (path.isNotEmpty() && !path.startsWith("/")) {
        "/$path"
    } else {
        path
    }

    val fixedUrl = "https://$fixedHost.$proxyServerHost$fixedPath"
    return Uri.parse(fixedUrl)
}

fun Uri.loadDrawable(
    context: Context,
    @ColorInt color: Int = Color.TRANSPARENT
): Drawable? {
    if (!isLocal) {
        return null
    }
    return if (pathSegments.isEmpty()) {
        ColorDrawable()
    } else {
        val resourceId = pathSegments[0].toInt()
        context.drawable(resourceId, color)
    }
}
