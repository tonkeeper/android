package com.tonapps.deposit.multicoin.screens.confirm.base

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.wallet.data.plugins.PluginsRepository

// Whether a transaction signed for [Account] by key would only be rejected: a TON wallet whose 2FA extension
// turned signing by key off.
fun interface SignatureGate {
    suspend fun isSignatureDisabled(account: Account): Boolean
}

class TonSignatureGate(private val pluginsRepository: PluginsRepository) : SignatureGate {

    override suspend fun isSignatureDisabled(account: Account): Boolean {
        if (!account.chain.isTvm) {
            return false
        }
        val network = when (account.chain.network.mode) {
            Network.Mode.Testnet -> TonNetwork.TESTNET
            Network.Mode.Mainnet -> TonNetwork.MAINNET
        }
        return pluginsRepository.isSignatureDisabled(account.address.display, network)
    }
}

class SignatureDisabledCause : Exception("signature disabled"), ConfirmationErrorCause {
    override val confirmationError: ConfirmationError get() = ConfirmationError.SignatureDisabled
}
