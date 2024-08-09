package com.tonapps.tonkeeper.extensions

import android.net.Uri

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

