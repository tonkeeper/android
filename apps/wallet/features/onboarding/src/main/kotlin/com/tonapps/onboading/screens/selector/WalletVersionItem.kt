package com.tonapps.onboading.screens.selector

import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.wallet.data.multichain.account.AccountWithDetails

// null fiat -> the cell falls back to the account's raw TON amount (no usable TON rate, or asset
// data unavailable).
data class WalletVersionItem(
    val account: AccountWithDetails,
    val fiat: WalletVersionFiat?,
)

data class WalletVersionFiat(
    val balance: DisplayUnit,
    val currency: FiatCurrency,
    val nftCount: Int,
)
