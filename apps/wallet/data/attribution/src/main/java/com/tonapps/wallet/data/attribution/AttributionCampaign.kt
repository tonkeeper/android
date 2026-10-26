package com.tonapps.wallet.data.attribution

import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.wallet.data.attribution.entities.AttributionProvider
import com.tonapps.wallet.data.attribution.entities.AttributionTouch
import java.net.URLEncoder

private const val BRANCH_LINK_ID = "~id"
private const val BRANCH_CHANNEL = "~channel"
private const val BRANCH_FEATURE = "~feature"
private const val BRANCH_CAMPAIGN = "~campaign"
private const val LINKRUNNER_CAMPAIGN_ID = "campaign_id"
private const val LINKRUNNER_CAMPAIGN_NAME = "campaign_name"

internal val AttributionTouch.campaignUtm: UtmParameters
    get() = when (provider) {
        AttributionProvider.Branch -> branchUtm(params)
        AttributionProvider.Linkrunner -> UtmParameters.fromLink(link)
    }

internal val AttributionTouch.campaignId: String?
    get() = when (provider) {
        AttributionProvider.Branch -> params.nonBlank(BRANCH_LINK_ID)
        AttributionProvider.Linkrunner -> params.nonBlank(LINKRUNNER_CAMPAIGN_ID)
    }

internal val AttributionTouch.campaignName: String?
    get() = when (provider) {
        AttributionProvider.Branch -> params.nonBlank(BRANCH_CAMPAIGN)
        AttributionProvider.Linkrunner -> params.nonBlank(LINKRUNNER_CAMPAIGN_NAME)
    }

internal fun branchUtm(params: Map<String, String>): UtmParameters {
    val clicked = UtmParameters.fromValues(params)
    if (!clicked.isEmpty) {
        return clicked
    }
    return UtmParameters(
        source = params.nonBlank(BRANCH_CHANNEL),
        medium = params.nonBlank(BRANCH_FEATURE),
        campaign = params.nonBlank(BRANCH_CAMPAIGN),
    )
}

internal fun String.withCampaignUtm(utm: UtmParameters): String {
    if (utm.isEmpty || !UtmParameters.fromLink(this).isEmpty) {
        return this
    }
    val fragmentStart = indexOf('#').takeIf { it >= 0 } ?: length
    val base = substring(0, fragmentStart)
    val separator = if (base.contains('?')) {
        '&'
    } else {
        '?'
    }
    val query = utm.toEventProps().entries.joinToString("&") { (key, value) ->
        "$key=${URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")}"
    }
    return "$base$separator$query${substring(fragmentStart)}"
}

private fun Map<String, String>.nonBlank(key: String): String? {
    return get(key)?.trim()?.ifEmpty { null }
}
