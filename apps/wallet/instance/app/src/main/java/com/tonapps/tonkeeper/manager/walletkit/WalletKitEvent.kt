package com.tonapps.tonkeeper.manager.walletkit

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import io.ton.walletkit.request.TONWalletSignDataRequest
import io.ton.walletkit.request.TONWalletTransactionRequest
import io.ton.walletkit.request.TONWalletConnectionRequest

sealed class WalletKitEvent(
    open val wallet: WalletEntity?,
) {
    
    data class SendTransactionRequest(
        val request: TONWalletTransactionRequest,
        override val wallet: WalletEntity,
    ) : WalletKitEvent(wallet)
    
    data class SignDataRequest(
        val request: TONWalletSignDataRequest,
        override val wallet: WalletEntity,
    ) : WalletKitEvent(wallet)

    data class ConnectionRequest(
        val request: TONWalletConnectionRequest,
        var fromPackageName: String?
    ) : WalletKitEvent(null)
}
