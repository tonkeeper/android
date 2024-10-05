package com.tonapps.tonkeeper.ui.screen.ledger.proof


import android.os.Bundle
import uikit.base.BaseArgs
import java.math.BigInteger

data class LedgerProofArgs(
    val domain: String,
    val timestamp: BigInteger,
    val payload: String,
    val walletId: String
) : BaseArgs() {

    private companion object {
        private const val ARG_DOMAIN = "domain"
        private const val ARG_TIMESTAMP = "timestamp"
        private const val ARG_PAYLOAD = "payload"
        private const val ARG_WALLET_ID = "wallet_id"
    }

    constructor(bundle: Bundle) : this(
        domain = bundle.getString(ARG_DOMAIN)!!,
        timestamp = BigInteger(bundle.getString(ARG_TIMESTAMP)!!),
        payload = bundle.getString(ARG_PAYLOAD)!!,
        walletId = bundle.getString(ARG_WALLET_ID)!!
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putString(ARG_DOMAIN, domain)
        putString(ARG_TIMESTAMP, timestamp.toString())
        putString(ARG_PAYLOAD, payload)
        putString(ARG_WALLET_ID, walletId)
    }

}