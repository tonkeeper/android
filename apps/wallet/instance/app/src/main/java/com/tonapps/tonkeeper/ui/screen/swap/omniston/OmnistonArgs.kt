package com.tonapps.tonkeeper.ui.screen.swap.omniston

import android.os.Bundle
import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.extensions.getParcelableCompat
import uikit.base.BaseArgs

data class OmnistonArgs(
    val fromToken: String,
    val toToken: String?,
    val initiator: ConfirmInitiator,
    val utm: UtmParameters,
): BaseArgs() {

    constructor(bundle: Bundle) : this(
        fromToken = bundle.getString(ARG_FROM_TOKEN)!!,
        toToken = bundle.getString(ARG_TO_TOKEN),
        initiator = ConfirmInitiator.entries.firstOrNull { it.name == bundle.getString(ARG_INITIATOR) }
            ?: ConfirmInitiator.User,
        utm = bundle.getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty,
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putString(ARG_FROM_TOKEN, fromToken)
        putString(ARG_TO_TOKEN, toToken)
        putString(ARG_INITIATOR, initiator.name)
        putParcelable(ARG_UTM, utm)
    }

    private companion object {
        private const val ARG_FROM_TOKEN = "from"
        private const val ARG_TO_TOKEN = "to"
        private const val ARG_INITIATOR = "initiator"
        private const val ARG_UTM = "utm"
    }
}
