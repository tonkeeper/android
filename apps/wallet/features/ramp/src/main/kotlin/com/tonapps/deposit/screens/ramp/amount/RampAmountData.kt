package com.tonapps.deposit.screens.ramp.amount

import com.tonapps.deposit.screens.method.RampAsset
import com.tonapps.deposit.screens.ramp.RampType
import io.exchangeapi.models.ExchangeDirection

data class RampAmountData(
    val assetFrom: RampAsset,
    val assetTo: RampAsset,
    val paymentMethodType: String,
) {
    val fromNetwork: String? = assetFrom.network
    val toNetwork: String? = assetTo.network

    val purchaseType: ExchangeDirection = when {
        fromNetwork == null -> ExchangeDirection.buy
        toNetwork == null -> ExchangeDirection.sell
        else -> ExchangeDirection.swap
    }

    val rampType: RampType = when {
        fromNetwork == null -> RampType.RampOn
        toNetwork == null -> RampType.RampOff
        else -> throw IllegalStateException("Can't be so!")
    }

    val cryptoAsset: RampAsset = if (fromNetwork != null) assetFrom else assetTo
    val fiatAsset: RampAsset = if (fromNetwork != null) assetTo else assetFrom
    val cryptoCode: String = cryptoAsset.currencyCode
    val fiatCode: String = fiatAsset.currencyCode
}
