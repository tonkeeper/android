package com.tonapps.deposit.screens.network

import com.tonapps.blockchain.model.legacy.WalletCurrency

data class CryptoNetworkInfo(
    val currency: WalletCurrency,
    val networkImage: String?,
    val fee: String?,
    val minAmount: String?,
    val providerMinAmount: String?,
    val providerMaxAmount: String?,
)
