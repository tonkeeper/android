package com.tonapps.wallet.data.attribution.appsflyer

import java.net.URLDecoder

private const val CLICK_TRANSACTION_KEY = "af_tranid"
private const val PARAM_SEPARATOR = '&'
private const val VALUE_SEPARATOR = '='
private const val DEEP_LINK_KEY_PREFIX = "deep_link_"

internal fun appsFlyerDeferredAppLink(linkValues: Map<String, String>, installReferrer: String?): String {
    if (!campaignUtmOf(linkValues).isEmpty) {
        return appsFlyerAppLink(linkValues)
    }
    val referrerCampaign = appsFlyerReferrerValuesOf(installReferrer).filterKeys { !it.startsWith(DEEP_LINK_KEY_PREFIX) }
    return appsFlyerAppLink(referrerCampaign + linkValues)
}

internal fun appsFlyerReferrerValuesOf(installReferrer: String?): Map<String, String> {
    if (installReferrer.isNullOrBlank()) {
        return emptyMap()
    }
    val params = installReferrer.split(PARAM_SEPARATOR)
        .map { param -> decodeReferrerPart(param.substringBefore(VALUE_SEPARATOR)) to decodeReferrerPart(param.substringAfter(VALUE_SEPARATOR, "")) }
        .filter { (name, _) -> name.isNotBlank() }
        .toMap()
    val values = appsFlyerValuesOf(params)
    if (CLICK_TRANSACTION_KEY !in values) {
        return emptyMap()
    }
    return values
}

private fun decodeReferrerPart(part: String): String {
    return try {
        URLDecoder.decode(part, Charsets.UTF_8.name())
    } catch (e: IllegalArgumentException) {
        part
    }
}
