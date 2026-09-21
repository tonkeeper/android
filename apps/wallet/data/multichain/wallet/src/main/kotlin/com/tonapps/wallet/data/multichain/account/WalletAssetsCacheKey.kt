package com.tonapps.wallet.data.multichain.account

import com.tonapps.wallet.data.cache.appendNullablePart
import com.tonapps.wallet.data.cache.appendPart

fun walletAssetsCacheKey(
    walletId: String,
    currency: String,
    availableOnly: Boolean,
    showHidden: Boolean,
    showAll: Boolean,
    query: String?,
    network: String?,
    capabilities: List<AssetCapability>? = null,
    verifiedOnly: Boolean = false,
    hideDust: Boolean = false,
): String = buildString {
    appendPart(walletId)
    appendPart(currency)
    appendPart(availableOnly.toString())
    appendPart(showHidden.toString())
    appendPart(showAll.toString())
    appendNullablePart(query)
    appendNullablePart(network)
    appendNullablePart(capabilities?.map { it.id }?.sorted()?.joinToString(","))
    appendPart(verifiedOnly.toString())
    appendPart(hideDust.toString())
}
