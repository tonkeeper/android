package com.tonapps.core.extensions

import android.content.Context
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.uikit.icon.UIKitIcon

fun WalletCurrency.Chain.iconExternalUrl(context: Context): String? {
    return when (this) {
        is WalletCurrency.Chain.TON -> context.externalDrawableUrl(UIKitIcon.ic_ton)
        is WalletCurrency.Chain.ETC,
        is WalletCurrency.Chain.ETHEREUM -> context.externalDrawableUrl(UIKitIcon.ic_eth)
        is WalletCurrency.Chain.TRON -> context.externalDrawableUrl(UIKitIcon.ic_tron)
        else -> null
    }
}

fun WalletCurrency.Chain.chainIcon(): Int? {
    return when (this) {
        is WalletCurrency.Chain.TON -> UIKitIcon.ic_ton
        is WalletCurrency.Chain.ETC,
        is WalletCurrency.Chain.ETHEREUM -> UIKitIcon.ic_eth
        is WalletCurrency.Chain.TRON -> UIKitIcon.ic_tron
        else -> null
    }
}

fun WalletCurrency.iconExternalUrl(context: Context): String? {
    return if (this == WalletCurrency.TON) {
        context.externalDrawableUrl(UIKitIcon.ic_ton)
    } else if (code == WalletCurrency.USDT_KEY) {
        context.externalDrawableUrl(UIKitIcon.ic_usdt)
    } else {
        iconUrl
    }
}
