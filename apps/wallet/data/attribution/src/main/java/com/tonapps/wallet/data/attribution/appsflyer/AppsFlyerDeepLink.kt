package com.tonapps.wallet.data.attribution.appsflyer

import java.net.URLEncoder

private const val APP_LINK_PREFIX = "tonkeeper://"
private const val MAIN_SCREEN = "main"
private const val LOYALTY_SCREEN = "loyalty"
private const val REFERRAL_CODE_QUERY = "referral_code"
private const val SHORT_LINK_PATH_SEGMENTS = 2
private val ONELINK_HOSTS = setOf("keeper.onelink.me", BRANDED_ONELINK_HOST)

internal const val BRANDED_ONELINK_HOST = "go.tonkeeper.com"
internal const val DEEP_LINK_VALUE_PARAM = "deep_link_value"
internal const val REFERRAL_CODE_PARAM = "deep_link_sub1"

internal fun isOneLinkHost(host: String?): Boolean {
    return host?.lowercase() in ONELINK_HOSTS
}

internal fun isSdkResolvedOneLink(pathSegments: List<String>, deepLinkValue: String?): Boolean {
    return pathSegments.size >= SHORT_LINK_PATH_SEGMENTS && deepLinkValue.isNullOrBlank()
}

internal fun appsFlyerAppLink(linkValues: Map<String, String>): String {
    val screen = linkValues[DEEP_LINK_VALUE_PARAM]?.lowercase() ?: MAIN_SCREEN
    val query = buildMap {
        linkValues[REFERRAL_CODE_PARAM]?.takeIf { screen == LOYALTY_SCREEN }?.let { put(REFERRAL_CODE_QUERY, it) }
        putAll(campaignUtmOf(linkValues).toEventProps())
    }
    val appLink = APP_LINK_PREFIX + encodeLinkPart(screen)
    if (query.isEmpty()) {
        return appLink
    }
    return query.entries.joinToString(separator = "&", prefix = "$appLink?") { (name, value) ->
        "$name=${encodeLinkPart(value)}"
    }
}

private fun encodeLinkPart(value: String): String {
    return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
