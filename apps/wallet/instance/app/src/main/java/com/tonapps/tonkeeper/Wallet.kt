package com.tonapps.tonkeeper

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity

sealed class Wallet {
    abstract val id: String

    data class Legacy(val entity: WalletEntity) : Wallet() {
        override val id: String get() = entity.id
    }

    data class Multichain(val entity: McWalletEntity) : Wallet() {
        override val id: String get() = entity.id
    }
}
