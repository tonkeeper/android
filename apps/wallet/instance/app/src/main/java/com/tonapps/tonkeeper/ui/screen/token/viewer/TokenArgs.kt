package com.tonapps.tonkeeper.ui.screen.token.viewer

import android.os.Bundle
import uikit.base.BaseArgs

data class TokenArgs(
    val address: String,
    val name: String,
    val symbol: String,
    val rawUsde: Boolean,
    val eventsOnly: Boolean = false,
): BaseArgs() {

    constructor(bundle: Bundle) : this(
        address = bundle.getString(ARG_ADDRESS)!!,
        name = bundle.getString(ARG_NAME)!!,
        symbol = bundle.getString(ARG_SYMBOL)!!,
        rawUsde = bundle.getBoolean(ARG_RAW_USDE),
        eventsOnly = bundle.getBoolean(ARG_EVENTS_ONLY),
    )

    override fun toBundle() = Bundle().apply {
        putString(ARG_ADDRESS, address)
        putString(ARG_NAME, name)
        putString(ARG_SYMBOL, symbol)
        putBoolean(ARG_RAW_USDE, rawUsde)
        putBoolean(ARG_EVENTS_ONLY, eventsOnly)
    }

    companion object {
        private const val ARG_ADDRESS = "address"
        private const val ARG_NAME = "name"
        private const val ARG_SYMBOL = "symbol"
        private const val ARG_RAW_USDE = "raw_usde"
        private const val ARG_EVENTS_ONLY = "events_only"
    }
}