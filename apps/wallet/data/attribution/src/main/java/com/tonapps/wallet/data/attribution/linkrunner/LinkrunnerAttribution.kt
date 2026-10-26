package com.tonapps.wallet.data.attribution.linkrunner

import android.content.Context
import android.net.Uri
import com.tonapps.wallet.data.attribution.AttributedClick
import com.tonapps.wallet.data.attribution.entities.AttributionEvent
import com.tonapps.wallet.data.attribution.entities.AttributionProvider
import com.tonapps.wallet.data.attribution.entities.AttributionTouch
import com.tonapps.wallet.data.attribution.toAppDeepLink
import io.linkrunner.sdk.LinkRunner
import io.linkrunner.sdk.models.response.CampaignData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

internal class LinkrunnerAttribution(private val context: Context) {

    private val initialization = CompletableDeferred<Boolean>()

    suspend fun initialize(token: String, isDebug: Boolean): Boolean {
        var isInitialized = false
        try {
            isInitialized = token.isNotBlank() && LinkRunner.getInstance().init(
                context = context,
                token = token,
                debug = isDebug,
            ).isSuccess
        } finally {
            initialization.complete(isInitialized)
        }
        return isInitialized
    }

    fun isLinkrunnerLink(uri: Uri): Boolean {
        return uri.host == HOST && (uri.scheme == "https" || uri.scheme == "http")
    }

    suspend fun open(uri: Uri): AttributedClick {
        val resolvedDeepLink = resolveDeepLink(uri)
        val params = buildMap {
            uri.queryParameterNames.forEach { name ->
                uri.getQueryParameter(name)?.let { put(name, it) }
            }
            resolvedDeepLink?.let { put(DEEPLINK, it) }
        }
        val deepLink = resolvedDeepLink?.let { resolved -> configuredDeepLink(uri, resolved) }
            ?: uri.takeIf { it.getQueryParameter(CAMPAIGN) != null }?.toAppDeepLink()
        return AttributedClick(
            touch = AttributionTouch(
                provider = AttributionProvider.Linkrunner,
                event = AttributionEvent.Open,
                attributedAt = System.currentTimeMillis(),
                link = uri.toString(),
                params = params,
            ),
            deepLink = deepLink,
        )
    }

    suspend fun installAttribution(): LinkrunnerInstallAttribution {
        if (!initialization.await()) {
            return LinkrunnerInstallAttribution.Unavailable
        }
        val data = LinkRunner.getInstance().getAttributionData().getOrNull()
            ?: return LinkrunnerInstallAttribution.Unavailable
        val campaignParams = campaignParams(data.campaignData)
        val isOrganic = campaignParams[CAMPAIGN_TYPE].equals(ORGANIC_CAMPAIGN_TYPE, ignoreCase = true)
        if (campaignParams[CAMPAIGN_ID].isNullOrBlank() || isOrganic) {
            return LinkrunnerInstallAttribution.NotAttributed
        }
        return LinkrunnerInstallAttribution.Attributed(
            AttributionTouch(
                provider = AttributionProvider.Linkrunner,
                event = AttributionEvent.Install,
                attributedAt = System.currentTimeMillis(),
                link = data.deeplink,
                params = campaignParams,
            ),
        )
    }

    private suspend fun resolveDeepLink(uri: Uri): String? {
        return withTimeoutOrNull(RESOLVE_TIMEOUT_MILLIS) {
            if (initialization.await()) {
                LinkRunner.getInstance().handleDeeplink(uri.toString()).getOrNull()?.deeplink
            } else {
                null
            }
        }?.takeIf { it.isNotBlank() }
    }

    private fun configuredDeepLink(clicked: Uri, resolved: String): Uri? {
        val clickedLink = clicked.toString()
        if (resolved.startsWith(clickedLink)) {
            return resolved.removePrefix(clickedLink).toAppDeepLink()
        }
        return Uri.parse(resolved).toAppDeepLink()
    }

    private fun Uri.toAppDeepLink(): Uri? {
        if (host != HOST) {
            return toString().toAppDeepLink()
        }
        val appLink = encodedPath?.trim('/')?.toAppDeepLink() ?: return null
        val builder = appLink.buildUpon()
        queryParameterNames.filter { name -> name != CAMPAIGN }.forEach { name ->
            getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
        }
        return builder.build()
    }

    private fun campaignParams(campaign: CampaignData?): Map<String, String> {
        campaign ?: return emptyMap()
        return listOf<Pair<String, String?>>(
            CAMPAIGN_ID to campaign.id,
            "campaign_name" to campaign.name,
            CAMPAIGN_TYPE to campaign.type,
            "ad_network" to campaign.adNetwork,
            "installed_at" to campaign.installedAt,
            "store_click_at" to campaign.storeClickAt,
            "group_name" to campaign.groupName,
            "asset_name" to campaign.assetName,
            "asset_group_name" to campaign.assetGroupName,
            "ad_network_campaign_id" to campaign.adNetworkCampaignId,
            "ad_set_id" to campaign.adSetId,
            "ad_set_name" to campaign.adSetName,
            "ad_creative_id" to campaign.adCreativeId,
            "ad_creative_name" to campaign.adCreativeName,
        ).mapNotNull { (key, value) ->
            value?.takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()
    }

    private companion object {
        const val HOST = "link.tonkeeper.com"
        const val CAMPAIGN = "c"
        const val CAMPAIGN_ID = "campaign_id"
        const val CAMPAIGN_TYPE = "campaign_type"
        const val ORGANIC_CAMPAIGN_TYPE = "ORGANIC"
        const val DEEPLINK = "deeplink"
        const val RESOLVE_TIMEOUT_MILLIS = 5_000L
    }
}
