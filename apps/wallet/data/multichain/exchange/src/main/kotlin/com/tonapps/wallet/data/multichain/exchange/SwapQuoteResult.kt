package com.tonapps.wallet.data.multichain.exchange

sealed interface SwapQuoteResult {
    data class Found(val quote: SwapQuote) : SwapQuoteResult
    data class NoRoute(val reason: SwapNoRouteReason) : SwapQuoteResult
}

enum class SwapNoRouteReason {
    RegionBlocked,
    AmountTooSmall,
    PairUnavailable,
    AssetUnavailable,
    ProviderUnavailable,
    Unknown,
}
