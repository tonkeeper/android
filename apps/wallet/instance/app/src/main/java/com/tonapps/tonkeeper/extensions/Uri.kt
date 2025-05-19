package com.tonapps.tonkeeper.extensions

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Size
import androidx.annotation.ColorInt
import com.tonapps.extensions.containsQuery
import com.tonapps.extensions.isLocal
import com.tonapps.extensions.query
import uikit.extensions.drawable
import androidx.core.net.toUri

private val utmQueryKeys = listOf(
    "utm_source",
    "utm_medium",
    "utm_campaign",
    "utm_term",
    "utm_content"
)

fun Uri.isTonSite(): Boolean {
    return this.host?.endsWith(".ton") ?: false
}

fun Uri.withUtmSource(source: String = "tonkeeper"): Uri {
    if (containsQuery("utm_source")) {
        return this
    }
    return this.buildUpon().appendQueryParameter("utm_source", source).build()
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
    return fixedUrl.toUri()
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

fun Uri.hasUtmSource(): Boolean {
    for (utmKey in utmQueryKeys) {
        if (containsQuery(utmKey)) {
            return true
        }
    }
    return false
}

fun Uri.hasRefer(): Boolean {
    return containsQuery("referrer") || containsQuery("ref")
}