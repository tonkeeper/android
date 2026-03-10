package com.tonapps.tonkeeper.ui.screen.swap.omniston

import android.os.Bundle
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import uikit.base.BaseArgs

data class OmnistonArgs(
    val fromToken: WalletCurrency,
    val toToken: WalletCurrency
): BaseArgs() {

    private companion object {
        private const val ARG_FROM_TOKEN = "from"
        private const val ARG_TO_TOKEN = "to"
    }

    constructor(bundle: Bundle) : this(
        fromToken = bundle.getParcelableCompat(ARG_FROM_TOKEN)!!,
        toToken = bundle.getParcelableCompat(ARG_TO_TOKEN)!!
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putParcelable(ARG_FROM_TOKEN, fromToken)
        putParcelable(ARG_TO_TOKEN, toToken)
    }
}