package com.tonapps.wallet.api.tron.entity

import com.tonapps.icu.Coins

data class TronResourcePrices(
    val energy: Coins,
    val bandwidth: Coins
)
