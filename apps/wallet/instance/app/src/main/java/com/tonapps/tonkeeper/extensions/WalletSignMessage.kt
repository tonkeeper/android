package com.tonapps.tonkeeper.extensions

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.core.flags.WalletFeature

/**
 * TON Connect `signMessage` signs an `internal_signed` W5 request with the wallet's own key, so only
 * mnemonic W5 R1 wallets qualify: gasless relayers take the R1 layout, not the beta's, Ledger has no
 * internal-signed path, and external signers, watch-only and lockup wallets are left out as on iOS.
 */
val WalletEntity.isTonConnectSignMessageAvailable: Boolean
    get() = version == WalletVersion.V5R1 && when (type) {
        WalletType.Default, WalletType.Testnet, WalletType.Multichain -> true
        else -> false
    }

/** Non-null when `SignMessage` should be advertised to a dApp: the wallet qualifies and the flag is on. */
fun WalletEntity.tonConnectSignMessageMaxMessages(): Int? {
    return if (WalletFeature.TonConnectSignMessage.isEnabled && isTonConnectSignMessageAvailable) {
        maxMessages
    } else {
        null
    }
}
