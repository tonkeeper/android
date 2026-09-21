package com.tonapps.wallet.data.multichain.account

import io.walletapi.apis.WalletsApi.CapabilitiesGetWalletAssets

enum class AssetCapability(val id: String) {
    Swap("swap"),
    Onramp("onramp"),
    Offramp("offramp"),
    P2p("p2p"),
}

internal fun AssetCapability.toApi(): CapabilitiesGetWalletAssets = when (this) {
    AssetCapability.Swap -> CapabilitiesGetWalletAssets.swap
    AssetCapability.Onramp -> CapabilitiesGetWalletAssets.onramp
    AssetCapability.Offramp -> CapabilitiesGetWalletAssets.offramp
    AssetCapability.P2p -> CapabilitiesGetWalletAssets.p2p
}
