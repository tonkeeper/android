package com.tonapps.tonkeeper.extensions

/**
 * Origin rules for `addDocumentStartJavaScript` and `addWebMessageListener` that cover an https
 * site together with its subdomains. Empty when the site cannot be covered at all.
 */
internal fun siteOriginRules(scheme: String?, host: String?, port: Int): Set<String> {
    if (scheme != "https" || host.isNullOrBlank()) {
        return emptySet()
    }
    val suffix = if (port == -1) {
        ""
    } else {
        ":$port"
    }
    val exact = "https://$host$suffix"
    // A wildcard rule matches subdomains but not the host itself, and has no form for an IP
    // literal, where an invalid rule would throw.
    return if (host.isIpLiteral()) {
        setOf(exact)
    } else {
        setOf(exact, "https://*.$host$suffix")
    }
}

private fun String.isIpLiteral(): Boolean {
    return startsWith("[") || all { it.isDigit() || it == '.' }
}
