package com.tonapps.wallet.data.passcode

import com.tonapps.chainkit.core.chain.model.account.Chain

/** Chains the wallets missed when the lock screen was passed, while they get added. */
sealed interface NewChainsSync {
    val chains: List<Chain>

    data class Running(override val chains: List<Chain>) : NewChainsSync

    // [added] is empty when nothing could be added; the rest is retried on the next unlock.
    data class Finished(override val chains: List<Chain>, val added: List<Chain>) : NewChainsSync
}
