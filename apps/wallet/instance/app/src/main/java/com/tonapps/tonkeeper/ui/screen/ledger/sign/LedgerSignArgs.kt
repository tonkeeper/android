package com.tonapps.tonkeeper.ui.screen.ledger.sign

import android.os.Bundle
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.ledger.ton.Transaction
import uikit.base.BaseArgs

data class LedgerSignArgs(
    val transaction: Transaction,
    val walletId: String,
    val transactionIndex: Int,
    val transactionCount: Int,
) : BaseArgs() {

    private companion object {
        private const val ARG_TRANSACTION = "transaction"
        private const val ARG_WALLET_ID = "wallet_id"
        private const val ARG_TRANSACTION_INDEX = "transaction_index"
        private const val ARG_TRANSACTION_COUNT = "transaction_count"
    }

    constructor(bundle: Bundle) : this(
        transaction = bundle.getParcelableCompat(ARG_TRANSACTION)!!,
        walletId = bundle.getString(ARG_WALLET_ID)!!,
        transactionIndex = bundle.getInt(ARG_TRANSACTION_INDEX),
        transactionCount = bundle.getInt(ARG_TRANSACTION_COUNT),
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putParcelable(ARG_TRANSACTION, transaction)
        putString(ARG_WALLET_ID, walletId)
        putInt(ARG_TRANSACTION_INDEX, transactionIndex)
        putInt(ARG_TRANSACTION_COUNT, transactionCount)
    }

}