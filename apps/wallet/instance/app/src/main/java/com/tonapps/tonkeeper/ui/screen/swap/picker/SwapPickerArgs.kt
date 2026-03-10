package com.tonapps.tonkeeper.ui.screen.swap.picker

import android.os.Bundle
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.wallet.data.core.currency.WalletCurrency
import uikit.base.BaseArgs

data class SwapPickerArgs(
    val selectedCurrency: WalletCurrency,
    val ignoreCurrency: WalletCurrency,
    val send: Boolean
): BaseArgs() {

    companion object {
        private const val ARG_SELECTED_CURRENCY = "selected_currency"
        private const val ARG_IGNORE_CURRENCY = "ignore_currency"
        private const val ARG_SEND = "send"
    }

    constructor(bundle: Bundle) : this(
        selectedCurrency = bundle.getParcelableCompat(ARG_SELECTED_CURRENCY)!!,
        ignoreCurrency = bundle.getParcelableCompat(ARG_IGNORE_CURRENCY)!!,
        send = bundle.getBoolean(ARG_SEND, false)
    )

    override fun toBundle() = Bundle().apply {
        putParcelable(ARG_SELECTED_CURRENCY, selectedCurrency)
        putParcelable(ARG_IGNORE_CURRENCY, ignoreCurrency)
        putBoolean(ARG_SEND, send)
    }
}