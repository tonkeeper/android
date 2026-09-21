package com.tonapps.portfolio.wallet

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity

sealed class CommonWallet {

    abstract val id: String
    abstract val name: String
    abstract val emoji: CharSequence
    abstract val color: Int
    abstract val type: WalletType
    abstract val version: WalletVersion?

    class Mc(val wallet: McWalletEntity) : CommonWallet() {
        override val id: String = wallet.id
        override val name: String = wallet.name
        override val emoji: CharSequence = wallet.emoji
        override val color: Int = wallet.color
        override val type: WalletType = wallet.type.legacy
        override val version: WalletVersion? = null
    }

    class Legacy(val wallet: WalletEntity) : CommonWallet() {
        override val id: String = wallet.id
        override val name: String = wallet.label.name
        override val emoji: CharSequence = wallet.label.emoji
        override val color: Int = wallet.label.color
        override val type: WalletType = wallet.type
        override val version: WalletVersion = wallet.version
    }
}
