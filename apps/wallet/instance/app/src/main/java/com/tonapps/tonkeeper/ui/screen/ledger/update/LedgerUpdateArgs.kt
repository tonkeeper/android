package com.tonapps.tonkeeper.ui.screen.ledger.update

import android.os.Bundle
import uikit.base.BaseArgs

data class LedgerUpdateArgs(
    val requiredVersion: String,
) : BaseArgs() {

    private companion object {
        private const val ARG_REQUIRED_VERSION = "required_version"
    }

    constructor(bundle: Bundle) : this(
        requiredVersion = bundle.getString(ARG_REQUIRED_VERSION)!!,
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putString(ARG_REQUIRED_VERSION, requiredVersion)
    }

}
