package com.tonapps.wallet.data.multichain.account

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.data.settings.SettingsRepository

data class WalletAssetsFilters(
    val currency: String,
    val verifiedOnly: Boolean,
    val hideDust: Boolean,
)

fun SettingsRepository.walletAssetsFilters(walletId: String): WalletAssetsFilters {
    return WalletAssetsFilters(
        currency = currency.code,
        verifiedOnly = isSafeModeEnabled(walletId, TonNetwork.MAINNET),
        hideDust = hideDustAssets,
    )
}
