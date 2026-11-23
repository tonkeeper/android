package com.tonapps.tonkeeper.manager

import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmContext.Dapp.Source
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmResultListener
import com.tonapps.wallet.data.dapps.evm.EvmProviderRepository
import com.tonapps.wallet.data.dapps.wc.WcRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ConfirmResultDispatcher(
    private val walletConnect: WcRepository,
    private val evmProvider: EvmProviderRepository,
) : ConfirmResultListener {

    override suspend fun approved(context: ConfirmContext.Dapp, result: String) {
        when (context.source) {
            // The dapp is answered exactly once: an approval the relay did not take is followed by
            // a rejection, or the request would hang until it expires.
            Source.WalletConnect -> {
                val delivered = walletConnect.approveRequest(context.requestId, result).isSuccess
                if (!delivered) {
                    runCatching { walletConnect.rejectRequest(context.requestId, context.topic) }
                }
            }
            Source.Injected -> evmProvider.approve(context.requestId, result)
        }
    }

    // Called undispatched from the sheet's teardown: the relay call leaves the main thread, the
    // provider is answered at once.
    override suspend fun rejected(context: ConfirmContext.Dapp) {
        when (context.source) {
            Source.WalletConnect -> withContext(Async.Io) {
                walletConnect.rejectRequest(context.requestId, context.topic)
            }
            Source.Injected -> evmProvider.reject(context.requestId)
        }
    }

    override suspend fun failed(context: ConfirmContext.Dapp) {
        when (context.source) {
            Source.WalletConnect -> withContext(Async.Io) {
                walletConnect.rejectRequest(context.requestId, context.topic)
            }
            Source.Injected -> evmProvider.fail(context.requestId)
        }
    }

    override fun isGone(context: ConfirmContext.Dapp): Boolean {
        return when (context.source) {
            Source.WalletConnect -> walletConnect.pendingRequest(context.requestId) == null
            Source.Injected -> evmProvider.pendingRequest(context.requestId) == null
        }
    }

    override fun gone(context: ConfirmContext.Dapp): Flow<Unit> {
        return when (context.source) {
            Source.WalletConnect -> walletConnect.requestGone(context.requestId)
            Source.Injected -> evmProvider.requestGone(context.requestId)
        }
    }

    override suspend fun closedAsGone(context: ConfirmContext.Dapp) {
        when (context.source) {
            Source.WalletConnect -> withContext(Async.Io) {
                walletConnect.requestExpired(context.topic)
            }
            Source.Injected -> Unit
        }
    }
}
