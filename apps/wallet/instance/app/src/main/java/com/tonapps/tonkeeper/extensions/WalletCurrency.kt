package com.tonapps.tonkeeper.extensions

import android.content.Context
import android.text.SpannableStringBuilder
import com.tonapps.apps.wallet.api.R
import com.tonapps.wallet.data.core.currency.WalletCurrency
import uikit.extensions.badgeBlue
import uikit.extensions.badgeDefault
import uikit.extensions.badgeOrange
import uikit.extensions.badgeRed

fun WalletCurrency.Chain.iconExternalUrl(context: Context): String? {
    return when (this) {
        is WalletCurrency.Chain.TON -> context.externalDrawableUrl(R.drawable.ic_ton_with_bg)
        is WalletCurrency.Chain.ETC -> context.externalDrawableUrl(R.drawable.ic_eth_with_bg)
        is WalletCurrency.Chain.TRON -> context.externalDrawableUrl(com.tonapps.tonkeeperx.R.drawable.ic_tron)
        else -> {
            null
        }
    }
}

fun WalletCurrency.iconExternalUrl(context: Context): String? {
    return if (this == WalletCurrency.TON) {
        context.externalDrawableUrl(R.drawable.ic_ton_with_bg)
    } else if (code == WalletCurrency.USDT_KEY) {
        context.externalDrawableUrl(R.drawable.ic_usdt_with_bg)
    } else {
        iconUrl
    }
}

fun WalletCurrency.spannableCode(context: Context): CharSequence {
    if (!isUSDT) {
        return code.trim()
    }
    val builder = SpannableStringBuilder("USD₮")
    builder.append(" ")
    when (chain) {
        is WalletCurrency.Chain.TRON -> builder.badgeRed(context) {
            append(chain.name.uppercase().replace("TRON", "TRC20"))
        }
        is WalletCurrency.Chain.TON -> builder.badgeBlue(context) {
            append(chain.name.uppercase())
        }
        is WalletCurrency.Chain.BNB -> builder.badgeOrange(context) {
            append(chain.name.uppercase())
        }
        else -> builder.badgeDefault(context) {
            append(chain.name.uppercase())
        }
    }
    return builder
}