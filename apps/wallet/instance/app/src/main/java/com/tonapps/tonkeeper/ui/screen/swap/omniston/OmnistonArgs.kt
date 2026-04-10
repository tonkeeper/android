package com.tonapps.tonkeeper.ui.screen.swap.omniston

import android.os.Bundle
import uikit.base.BaseArgs

data class OmnistonArgs(
    val fromToken: String,
    val toToken: String?,
): BaseArgs() {

    private companion object {
        private const val ARG_FROM_TOKEN = "from"
        private const val ARG_TO_TOKEN = "to"
    }

    constructor(bundle: Bundle) : this(
        fromToken = bundle.getString(ARG_FROM_TOKEN)!!,
        toToken = bundle.getString(ARG_TO_TOKEN),
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putString(ARG_FROM_TOKEN, fromToken)
        putString(ARG_TO_TOKEN, toToken)
    }
}
