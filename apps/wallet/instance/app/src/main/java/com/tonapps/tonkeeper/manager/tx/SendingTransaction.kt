package com.tonapps.tonkeeper.manager.tx

import com.tonapps.blockchain.ton.extensions.parseCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.ton.cell.Cell

data class SendingTransaction(
    val wallet: WalletEntity,
    val boc: Cell,
    val timestamp: Long = System.currentTimeMillis()
) {

    val hash: String = boc.hash().toHex()

    constructor(wallet: WalletEntity, boc: String) : this(
        wallet = wallet,
        boc = boc.parseCell()
    )
}