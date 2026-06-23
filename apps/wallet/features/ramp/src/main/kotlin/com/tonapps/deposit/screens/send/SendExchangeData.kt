package com.tonapps.deposit.screens.send

import com.tonapps.blockchain.model.legacy.WalletCurrency
import kotlinx.serialization.Serializable

@Serializable
data class SendExchangeData(
    val exchangeTo: WalletCurrency,
    val withdrawalFee: String? = null,
    val fiatCurrency: WalletCurrency? = null,
    val minAmount: String? = null,
    val maxAmount: String? = null,
)
