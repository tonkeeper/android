package com.tonapps.tonkeeper.ui.screen.events.compose.history.paging

import com.tonapps.tonkeeper.ui.screen.events.compose.history.state.TxTronParams
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class TxTronParamsProvider(
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository,
) {

    private val isTronSupported: Boolean
        get() = wallet.hasPrivateKey && !wallet.testnet

    @OptIn(ExperimentalAtomicApi::class)
    private val atomicRef = AtomicReference<TxTronParams?>(null)

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun get(): TxTronParams? {
        if (!isTronSupported) {
            return null
        }
        return atomicRef.load() ?: create()
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun create(): TxTronParams? {
        val params = TxTronParams(
            address = accountRepository.getTronBlockchainAddress(wallet.id),
            walletId = wallet.id,
        )
        if (params.isEmtpy) {
            atomicRef.store(null)
            return null
        }
        atomicRef.store(params)
        return params
    }

}