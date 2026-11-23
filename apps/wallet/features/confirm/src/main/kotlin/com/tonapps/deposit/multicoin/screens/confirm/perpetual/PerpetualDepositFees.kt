package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import androidx.compose.runtime.Immutable
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.chainkit.core.chain.model.num.FormatOptions
import com.tonapps.chainkit.core.chain.model.num.Formatter

@Immutable
class PerpetualDepositFees(
    val total: String,
    val network: String,
    val provider: String,
    val symbol: String,
)

@Immutable
class PerpetualDepositDeliveryFees(
    val total: String,
    val network: String,
    val provider: String,
)

@Immutable
class PerpetualDepositPricing(
    val fees: PerpetualDepositFees?,
    val feeShareBps: Int?,
    val delivery: PerpetualDepositDeliveryFees?,
)

internal fun perpetualDepositPricing(
    feeSymbol: String,
    networkFeeUsd: BigDecimal?,
    providerFees: ConfirmType.PerpetualDeposit.ProviderFees?,
    spentUsd: BigDecimal?,
): PerpetualDepositPricing {
    val fees = if (networkFeeUsd != null && providerFees != null) {
        val network = networkFeeUsd + providerFees.destinationGasUsd
        PerpetualDepositFees(
            total = approximateUsd(network + providerFees.providerUsd),
            network = approximateUsd(network),
            provider = approximateUsd(providerFees.providerUsd),
            symbol = feeSymbol,
        )
    } else {
        null
    }
    val knownFeesUsd = (networkFeeUsd ?: BigDecimal.ZERO) +
        (providerFees?.let { it.destinationGasUsd + it.providerUsd } ?: BigDecimal.ZERO)
    val feeShareBps = spentUsd?.takeIf { it.isPositive }?.let { spent ->
        (knownFeesUsd * BPS).divide(spent, SHARE_MODE).intValue(exactRequired = false)
    }
    return PerpetualDepositPricing(fees = fees, feeShareBps = feeShareBps, delivery = deliveryFees(providerFees))
}

private fun deliveryFees(providerFees: ConfirmType.PerpetualDeposit.ProviderFees?): PerpetualDepositDeliveryFees? {
    providerFees ?: return null
    val total = providerFees.destinationGasUsd + providerFees.providerUsd
    if (!total.isPositive) {
        return null
    }
    return PerpetualDepositDeliveryFees(
        total = approximateUsd(total),
        network = approximateUsd(providerFees.destinationGasUsd),
        provider = approximateUsd(providerFees.providerUsd),
    )
}

private fun approximateUsd(usd: BigDecimal): String {
    return Formatter.formatFiat(value = usd, currency = USD, approximate = true, options = usdFormatOptions())
}

internal val USD = FiatCurrency("$")
internal fun usdFormatOptions(): FormatOptions = Formatter.defaultOptions.copy(symbolSpacing = " ")

private val BPS = BigDecimal.fromInt(10_000)
private val SHARE_MODE = DecimalMode(decimalPrecision = 30L, roundingMode = RoundingMode.FLOOR, scale = 0L)
