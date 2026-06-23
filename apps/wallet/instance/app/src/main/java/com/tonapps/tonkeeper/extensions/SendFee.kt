package com.tonapps.tonkeeper.extensions

import android.content.Context
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.data.settings.entities.PreferredTronFeeMethod
import com.tonapps.wallet.localization.Plurals

val SendFee.id: String
    get() = when (this) {
        is SendFee.Battery -> "battery"
        is SendFee.TokenFee -> amount.token.symbol
        else -> "unknown"
    }

val SendFee.TokenFee.symbol: String
    get() = amount.token.symbol

val SendFee.TokenFee.formattedAmount: CharSequence
    get() = CurrencyFormatter.format(amount.token.symbol, amount.value)

val SendFee.TokenFee.formattedFiat: CharSequence
    get() = CurrencyFormatter.formatFiat(fiatCurrency.code, fiatAmount)

fun SendFee.Battery.formattedCharges(context: Context): CharSequence {
    return context.resources.getQuantityString(
        Plurals.battery_charges,
        charges,
        CurrencyFormatter.format(value = charges.toBigDecimal())
    )
}

val SendFee.method: PreferredFeeMethod?
    get() = when (this) {
        is SendFee.Gasless -> PreferredFeeMethod.GASLESS
        is SendFee.Battery -> PreferredFeeMethod.BATTERY
        is SendFee.Ton -> PreferredFeeMethod.TON
        else -> null
    }

val SendFee.tronMethod: PreferredTronFeeMethod?
    get() = when (this) {
        is SendFee.Battery -> PreferredTronFeeMethod.BATTERY
        is SendFee.TronTrx -> PreferredTronFeeMethod.TRX
        is SendFee.TronTon -> PreferredTronFeeMethod.TON
        else -> null
    }