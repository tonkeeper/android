package com.tonapps.wallet.data.attribution.appsflyer

import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.InstallApp.InstallAppAttributionStatus

private const val ORGANIC_STATUS = "Organic"
private const val NON_ORGANIC_STATUS = "Non-organic"
private const val CAMPAIGN_ID_KEY = "campaign_id"
private const val CAMPAIGN_KEY = "campaign"

internal const val CONVERSION_STATUS_PARAM = "af_status"

internal data class AppsFlyerInstall(
    val status: InstallAppAttributionStatus,
    val campaignId: String? = null,
    val campaignName: String? = null,
    val utm: UtmParameters = UtmParameters.Empty,
)

internal fun appsFlyerInstallOf(conversionData: Map<String, Any?>): AppsFlyerInstall? {
    val values = appsFlyerValuesOf(conversionData)
    return when (values[CONVERSION_STATUS_PARAM]) {
        ORGANIC_STATUS -> AppsFlyerInstall(status = InstallAppAttributionStatus.Organic)
        NON_ORGANIC_STATUS -> AppsFlyerInstall(
            status = InstallAppAttributionStatus.NonOrganic,
            campaignId = values[CAMPAIGN_ID_KEY],
            campaignName = values[CAMPAIGN_KEY],
            utm = campaignUtmOf(values),
        )
        else -> null
    }
}
