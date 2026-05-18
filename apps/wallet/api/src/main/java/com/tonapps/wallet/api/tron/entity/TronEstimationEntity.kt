package com.tonapps.wallet.api.tron.entity

import com.tonapps.icu.Coins
import io.batteryapi.models.EstimatedTronTx

sealed class TronEstimationEntity {
    data class Charges(val charges: Int, val estimated: EstimatedTronTx)
    data class TrxFee(val fee: Coins)
    data class TonFee(val fee: Coins, val sendToAddress: String)
}
