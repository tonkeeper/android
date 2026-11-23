package com.tonapps.wallet.data.attribution.appsflyer

import com.tonapps.bus.core.contract.UtmParameters

private const val MEDIA_SOURCE_KEY = "media_source"
private const val LINK_MEDIA_SOURCE_KEY = "pid"
private const val CAMPAIGN_KEY = "campaign"
private const val LINK_CAMPAIGN_KEY = "c"
private const val CHANNEL_KEY = "af_channel"

internal fun appsFlyerValuesOf(values: Map<String, Any?>): Map<String, String> {
    return values.mapNotNull { (key, value) ->
        value?.toString()?.trim()?.ifEmpty { null }?.let { key to it }
    }.toMap()
}

internal fun campaignUtmOf(values: Map<String, String>): UtmParameters {
    val linkUtm = UtmParameters.fromValues(values)
    if (!linkUtm.isEmpty) {
        return linkUtm
    }
    return UtmParameters(
        source = values[MEDIA_SOURCE_KEY] ?: values[LINK_MEDIA_SOURCE_KEY],
        medium = values[CHANNEL_KEY],
        campaign = values[CAMPAIGN_KEY] ?: values[LINK_CAMPAIGN_KEY],
    )
}
