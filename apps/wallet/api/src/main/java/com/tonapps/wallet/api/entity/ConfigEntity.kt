package com.tonapps.wallet.api.entity

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import com.tonapps.extensions.toStringList
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.Constants
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class ConfigEntity(
    val empty: Boolean,
    val supportLink: String,
    val nftExplorer: String,
    val transactionExplorer: String,
    val accountExplorer: String,
    val mercuryoSecret: String,
    val tonapiMainnetHost: String,
    val tonapiTestnetHost: String,
    val tonConnectBridgeHost: String,
    val stonfiUrl: String,
    val tonNFTsMarketplaceEndpoint: String,
    val directSupportUrl: String,
    val tonkeeperNewsUrl: String,
    val tonCommunityUrl: String,
    val tonCommunityChatUrl: String,
    val tonApiV2Key: String,
    val featuredPlayInterval: Int,
    val flags: FlagsEntity,
    val faqUrl: String,
    val aptabaseEndpoint: String,
    val aptabaseAppKey: String,
    val scamEndpoint: String,
    val batteryHost: String,
    val batteryTestnetHost: String,
    val batteryBeta: Boolean,
    val batterySendDisabled: Boolean,
    val disableBatteryIapModule: Boolean,
    val disableBatteryCryptoRechargeModule: Boolean,
    val batteryMaxInputAmount: String,
    val batteryRefundEndpoint: String,
    val batteryPromoDisable: Boolean,
    val stakingInfoUrl: String,
    val tonapiSSEEndpoint: String,
    val tonapiSSETestnetEndpoint: String,
    val toncenterSSEEndpoint: String,
    val toncenterSSETestnetEndpoint: String,
    val iapPackages: List<IAPPackageEntity>,
    val burnZeroDomain: String,
    val scamAPIURL: String,
    val reportAmount: Coins,
    val stories: List<String>,
    val apkDownloadUrl: String?,
    val apkName: AppVersion?,
    val tronApiUrl: String,
    val enabledStaking: List<String>,
    val qrScannerExtends: List<QRScannerExtendsEntity>,
    val region: String,
    val tonkeeperApiUrl: String,
    val tronSwapUrl: String,
    val tronSwapTitle: String,
    val tronApiKey: String? = null,
    val privacyPolicyUrl: String,
    val termsOfUseUrl: String,
    val webSwapsUrl: String,
    val tronFeeFaqUrl: String,
): Parcelable {

    @IgnoredOnParcel
    val swapUri: Uri
        get() = stonfiUrl.toUri()

    @IgnoredOnParcel
    val domains: List<String> by lazy {
        listOf(tonapiMainnetHost, tonapiTestnetHost, tonapiSSEEndpoint, tonapiSSETestnetEndpoint, tonConnectBridgeHost, "https://tonapi.io/", "https://toncenterproxy.tonapi.io/")
    }

    @IgnoredOnParcel
    val apk: ApkEntity? by lazy {
        val name = apkName ?: return@lazy null
        val url = apkDownloadUrl ?: return@lazy null
        ApkEntity(url, name)
    }

    constructor(json: JSONObject, debug: Boolean) : this(
        empty = false,
        supportLink = json.getString("supportLink"),
        nftExplorer = json.getString("NFTOnExplorerUrl"),
        transactionExplorer = json.getString("transactionExplorer"),
        accountExplorer = json.getString("accountExplorer"),
        mercuryoSecret = json.getString("mercuryoSecret"),
        tonapiMainnetHost = json.getString("tonapiMainnetHost"),
        tonapiTestnetHost = json.getString("tonapiTestnetHost"),
        tonConnectBridgeHost = json.optString("ton_connect_bridge", "https://bridge.tonapi.io"),
        stonfiUrl = json.getString("stonfiUrl"),
        tonNFTsMarketplaceEndpoint = json.getString("tonNFTsMarketplaceEndpoint"),
        directSupportUrl = json.getString("directSupportUrl"),
        tonkeeperNewsUrl = json.getString("tonkeeperNewsUrl"),
        tonCommunityUrl = json.getString("tonCommunityUrl"),
        tonCommunityChatUrl = json.getString("tonCommunityChatUrl"),
        tonApiV2Key = json.getString("tonApiV2Key"),
        featuredPlayInterval = json.optInt("featured_play_interval", 3000),
        flags = FlagsEntity(json.getJSONObject("flags")), /*if (debug) {
            FlagsEntity()
        } else {
            FlagsEntity(json.getJSONObject("flags"))
        },*/
        faqUrl = json.getString("faq_url"),
        aptabaseEndpoint = json.getString("aptabaseEndpoint"),
        aptabaseAppKey = json.getString("aptabaseAppKey"),
        scamEndpoint = json.optString("scamEndpoint", "https://scam.tonkeeper.com"),
        batteryHost = json.optString("batteryHost", "https://battery.tonkeeper.com"),
        batteryTestnetHost = json.optString("batteryTestnetHost", "https://testnet-battery.tonkeeper.com"),
        batteryBeta = json.optBoolean("battery_beta", true),
        batterySendDisabled = json.optBoolean("disable_battery_send", false),
        disableBatteryIapModule = json.optBoolean("disable_battery_iap_module", false),
        disableBatteryCryptoRechargeModule = json.optBoolean("disable_battery_crypto_recharge_module", false),
        batteryMaxInputAmount = json.optString("batteryMaxInputAmount", "3"),
        batteryRefundEndpoint = json.optString("batteryRefundEndpoint", "https://battery-refund-app.vercel.app"),
        batteryPromoDisable = json.optBoolean("disable_battery_promo_module", true),
        stakingInfoUrl = json.getString("stakingInfoUrl"),
        tonapiSSEEndpoint = json.optString("tonapi_sse_endpoint", "https://rt.tonapi.io").removeSuffix("/"),
        tonapiSSETestnetEndpoint = json.optString("tonapi_sse_testnet_endpoint", "https://rt-testnet.tonapi.io").removeSuffix("/"),
        toncenterSSEEndpoint = json.optString("tonapi_sse_endpoint_v2", "https://tonapi.io").removeSuffix("/"),
        toncenterSSETestnetEndpoint = json.optString("tonapi_sse_testnet_endpoint_v2", "https://testnet.tonapi.io").removeSuffix("/"),
        iapPackages = json.optJSONArray("iap_packages")?.let { array ->
            (0 until array.length()).map { IAPPackageEntity(array.getJSONObject(it)) }
        } ?: emptyList(),
        burnZeroDomain = json.optString("burnZeroDomain", "UQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJKZ"), // tonkeeper-zero.ton
        scamAPIURL = json.optString("scam_api_url", "https://scam.tonkeeper.com"),
        reportAmount = Coins.of(json.optString("reportAmount") ?: "0.03"),
        stories = json.getJSONArray("stories").toStringList(),
        apkDownloadUrl = json.optString("apk_download_url"),
        apkName = json.optString("apk_name")?.let { AppVersion(it.removePrefix("v")) },
        tronApiUrl = json.optString("tron_api_url", "https://api.trongrid.io"),
        enabledStaking = json.optJSONArray("enabled_staking")?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        } ?: emptyList(),
        qrScannerExtends = json.optJSONArray("qr_scanner_extends")?.let { array ->
            QRScannerExtendsEntity.of(array)
        } ?: emptyList(),
        region = json.getString("region"),
        tonkeeperApiUrl = json.optString("tonkeeper_api_url", "https://api.tonkeeper.com"),
        tronSwapUrl = json.optString("tron_swap_url", "https://widget.letsexchange.io/en?affiliate_id=ffzymmunvvyxyypo&coin_from=ton&coin_to=USDT-TRC20&is_iframe=true"),
        tronSwapTitle = json.optString("tron_swap_title", "LetsExchange"),
        // tronApiKey = json.optString("tron_api_key"),
        privacyPolicyUrl = json.getString("privacy_policy"),
        termsOfUseUrl = json.getString("terms_of_use"),
        webSwapsUrl = json.optString("web_swaps_url", Constants.SWAP_API),
        tronFeeFaqUrl = json.getString("faq_tron_fee_url"),
    )

    constructor() : this(
        empty = true,
        supportLink = "mailto:support@tonkeeper.com",
        nftExplorer = "https://tonviewer.com/nft/%s",
        transactionExplorer = "https://tonviewer.com/transaction/%s",
        accountExplorer = "https://tonviewer.com/%s",
        mercuryoSecret = "",
        tonapiMainnetHost = "https://keeper.tonapi.io",
        tonapiTestnetHost = "https://testnet.tonapi.io",
        tonConnectBridgeHost = "https://bridge.tonapi.io",
        stonfiUrl = "https://swap-widget.tonkeeper.com",
        tonNFTsMarketplaceEndpoint = "https://ton.diamonds",
        directSupportUrl = "https://t.me/tonkeeper_supportbot",
        tonkeeperNewsUrl = "https://t.me/tonkeeper_new",
        tonCommunityUrl = "https://t.me/toncoin",
        tonCommunityChatUrl = "https://t.me/toncoin_chat",
        tonApiV2Key = "AF77F5JNEUSNXPQAAAAMDXXG7RBQ3IRP6PC2HTHL4KYRWMZYOUQGDEKYFDKBETZ6FDVZJBI",
        featuredPlayInterval = 3000,
        flags = FlagsEntity(),
        faqUrl = "https://tonkeeper.helpscoutdocs.com/",
        aptabaseEndpoint = "https://anonymous-analytics.tonkeeper.com",
        aptabaseAppKey = "A-SH-4314447490",
        scamEndpoint = "https://scam.tonkeeper.com",
        batteryHost = "https://battery.tonkeeper.com",
        batteryTestnetHost = "https://testnet-battery.tonkeeper.com",
        batteryBeta = true,
        batterySendDisabled = false,
        disableBatteryIapModule = false,
        disableBatteryCryptoRechargeModule = false,
        batteryMaxInputAmount = "3",
        batteryRefundEndpoint = "https://battery-refund-app.vercel.app",
        batteryPromoDisable = true,
        stakingInfoUrl = "https://ton.org/stake",
        tonapiSSEEndpoint = "https://rt.tonapi.io",
        tonapiSSETestnetEndpoint = "https://rt-testnet.tonapi.io",
        toncenterSSEEndpoint = "https://tonapi.io/streaming/v2/sse",
        toncenterSSETestnetEndpoint = "https://testnet.tonapi.io/streaming/v2/sse",
        iapPackages = emptyList(),
        burnZeroDomain = "UQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJKZ",
        scamAPIURL = "https://scam.tonkeeper.com",
        reportAmount = Coins.of("0.03"),
        stories = emptyList(),
        apkDownloadUrl = null,
        apkName = null,
        tronApiUrl = "https://api.trongrid.io",
        enabledStaking = emptyList(),
        qrScannerExtends = emptyList(),
        region = "US",
        tonkeeperApiUrl = "https://api.tonkeeper.com",
        tronSwapUrl = "https://widget.letsexchange.io/en?affiliate_id=ffzymmunvvyxyypo&coin_from=ton&coin_to=USDT-TRC20&is_iframe=true",
        tronSwapTitle = "LetsExchange",
        privacyPolicyUrl = "https://tonkeeper.com/privacy",
        termsOfUseUrl = "https://tonkeeper.com/terms",
        webSwapsUrl = Constants.SWAP_API,
        tronFeeFaqUrl = "https://tonkeeper.helpscoutdocs.com/article/137-multichain"
    )

    fun formatTransactionExplorer(testnet: Boolean, tron: Boolean, hash: String): String {
        return if (tron) {
            "https://tronscan.org/#/transaction/$hash"
        } else if (testnet) {
            "https://testnet.tonviewer.com/transaction/$hash"
        } else {
            transactionExplorer.format(hash)
        }
    }

    companion object {
        val default = ConfigEntity()
    }
}