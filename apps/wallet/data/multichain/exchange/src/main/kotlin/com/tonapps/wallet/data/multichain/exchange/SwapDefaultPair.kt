package com.tonapps.wallet.data.multichain.exchange

import com.tonapps.wallet.data.multichain.asset.AssetEntity

/**
 * Server-driven pair to pre-select on the swap screen, fetched from the aggregator config.
 *
 * @param source full source asset when the config carries it, which spares a `/asset` request.
 * @param destination full destination asset when the config carries it.
 */
data class SwapDefaultPair(
    val source: AssetEntity? = null,
    val destination: AssetEntity? = null,
)
