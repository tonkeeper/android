package com.tonapps.tonkeeper.extensions

import android.content.Context
import android.text.SpannableStringBuilder
import com.tonapps.wallet.data.core.currency.WalletCurrency
import uikit.extensions.badgeBlue
import uikit.extensions.badgeDefault
import uikit.extensions.badgeOrange
import uikit.extensions.badgeRed

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