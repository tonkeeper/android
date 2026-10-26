package com.tonapps.tonkeeper.manager

import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmContext.Dapp.Source
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmResultListener
import com.tonapps.wallet.data.dapps.evm.EvmProviderRepository
import com.tonapps.wallet.data.dapps.wc.WcRepository
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
}
