package com.tonapps.tonkeeper.ui.screen.staking.viewer

import android.os.Bundle
import uikit.base.BaseArgs

data class StakeViewerArgs(
    val address: String,
    val name: String
): BaseArgs() {

    private companion object {
        private const val ARG_ADDRESS = "address"
        private const val ARG_NAME = "name"
    }

    constructor(bundle: Bundle) : this(
        address = bundle.getString(ARG_ADDRESS)!!,
        name = bundle.getString(ARG_NAME)!!
    )

    override fun toBundle() = Bundle().apply {
        putString(ARG_ADDRESS, address)
        putString(ARG_NAME, name)
    }
}