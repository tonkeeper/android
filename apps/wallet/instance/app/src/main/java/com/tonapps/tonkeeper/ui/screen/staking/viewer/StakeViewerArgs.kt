package com.tonapps.tonkeeper.ui.screen.staking.viewer

import android.os.Bundle
import com.tonapps.wallet.api.entity.EthenaEntity
import uikit.base.BaseArgs

data class StakeViewerArgs(
    val address: String,
    val name: String,
    val ethenaType: String,
) : BaseArgs() {

    private companion object {
        private const val ARG_ADDRESS = "address"
        private const val ARG_NAME = "name"
        private const val ARG_ETHENA_TYPE = "ethena_type"
    }

    constructor(bundle: Bundle) : this(
        address = bundle.getString(ARG_ADDRESS)!!,
        name = bundle.getString(ARG_NAME)!!,
        ethenaType = bundle.getString(ARG_ETHENA_TYPE)!!
    )

    override fun toBundle() = Bundle().apply {
        putString(ARG_ADDRESS, address)
        putString(ARG_NAME, name)
        putString(ARG_ETHENA_TYPE, ethenaType)
    }
}