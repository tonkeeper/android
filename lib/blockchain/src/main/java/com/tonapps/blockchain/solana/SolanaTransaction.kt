package com.tonapps.blockchain.solana

import com.tonapps.chainkit.chain.solana.utils.SolanaUtils

object SolanaTransaction {

    /**
     * The account that pays a serialized transaction's fee — the first account key of its message,
     * base58 — or null when [base64] is not a transaction (legacy or versioned). Read by ChainKit's
     * own transaction decoder.
     */
    fun feePayer(base64: String): String? {
        return SolanaUtils.feePayer(base64)
    }
}
