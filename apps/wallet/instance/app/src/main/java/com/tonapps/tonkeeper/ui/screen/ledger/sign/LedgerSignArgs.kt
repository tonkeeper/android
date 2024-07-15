package com.tonapps.tonkeeper.ui.screen.ledger.sign

import android.os.Bundle
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.ledger.ton.Transaction
import uikit.base.BaseArgs

data class LedgerSignArgs(
    val transaction: Transaction,
    val walletId: String,
    val requestKey: String
) : BaseArgs() {

    private companion object {
        private const val ARG_TRANSACTION = "transaction"
        private const val ARG_WALLET_ID = "wallet_id"
        private const val ARG_REQUEST_KEY = "request_key"
    }

    constructor(bundle: Bundle) : this(
        transaction = bundle.getParcelableCompat(ARG_TRANSACTION)!!,
        walletId = bundle.getString(ARG_WALLET_ID)!!,
        requestKey = bundle.getString(ARG_REQUEST_KEY)!!
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putParcelable(ARG_TRANSACTION, transaction)
        putString(ARG_WALLET_ID, walletId)
        putString(ARG_REQUEST_KEY, requestKey)
    }

}