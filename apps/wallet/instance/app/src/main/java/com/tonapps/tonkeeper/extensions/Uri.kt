package com.tonapps.tonkeeper.extensions

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.core.net.toUri
import com.tonapps.extensions.containsQuery
import com.tonapps.extensions.isLocal
import uikit.extensions.drawable

private val utmQueryKeys = listOf(
    "utm_source",
    "utm_medium",
    "utm_campaign",
    "utm_term",
    "utm_content"
)

internal fun hostMatches(host: String?, base: String?): Boolean {
    val value = host?.lowercase() ?: return false
    val suffix = base?.lowercase() ?: return false
    return value == suffix || value.endsWith(".$suffix")
}

// A url without a port means the scheme default, which is also how WebView reads its origin rules.
private fun Uri.effectivePort(): Int = when {
    port != -1 -> port
    scheme.equals("https", ignoreCase = true) -> 443
    scheme.equals("http", ignoreCase = true) -> 80
    else -> -1
}

fun Uri.isSameOriginOrSubdomain(other: Uri): Boolean {
    return scheme.equals(other.scheme, ignoreCase = true) &&
        effectivePort() == other.effectivePort() &&
        hostMatches(host, other.host)
}

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

fun getUriForResourceId(resourceId: Int): Uri {
    return Uri.Builder().scheme("res").path(resourceId.toString()).build()
}