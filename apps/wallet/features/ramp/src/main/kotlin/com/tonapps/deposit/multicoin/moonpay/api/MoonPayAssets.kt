package com.tonapps.deposit.multicoin.moonpay.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MoonPayAssetSource(val value: String) {
    @SerialName("cefi")
    Cefi("cefi"),

    @SerialName("defi")
    Defi("defi"),
}

// CeFi assets are quoted by `code`, which already carries the network (`USDC_SOL`);
// DeFi tokens by `caip19`, because their `code` is not unique.
@Serializable
data class MoonPayPlatformAsset(
    val caip19: String = "",
    val code: String,
    val name: String? = null,
    val network: String = "",
    val source: MoonPayAssetSource? = null,
    // Null for the native token of a network.
    val contractAddress: String? = null,
    val decimals: Int? = null,
    val precision: Int? = null,
    val status: Status? = null,
) {

    val isAvailable: Boolean
        get() = status?.suspended != true && status?.disabled != true

    val quoteReference: MoonPayAsset
        get() = if (source == MoonPayAssetSource.Defi && caip19.isNotEmpty()) {
            MoonPayAsset(caip19 = caip19)
        } else {
            MoonPayAsset(code = code)
        }

    @Serializable
    data class Status(
        val suspended: Boolean? = null,
        val disabled: Boolean? = null,
    )
}

data class MoonPayAssetsPage(
    val assets: List<MoonPayPlatformAsset>,
    val nextCursor: String?,
)

@Serializable
internal class MoonPayAssetsResponse(
    val data: List<MoonPayPlatformAsset>,
    val pageInfo: PageInfo? = null,
) {

    @Serializable
    class PageInfo(val nextCursor: String? = null)
}
