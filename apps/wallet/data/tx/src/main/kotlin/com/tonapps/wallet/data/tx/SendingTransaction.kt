package com.tonapps.wallet.data.tx

import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.blockchain.model.legacy.WalletEntity
import org.ton.cell.Cell

data class SendingTransaction(
    val wallet: WalletEntity,
    val boc: Cell,
    val timestamp: Long = System.currentTimeMillis()
) {

    val hash: String = boc.hash().toHex()

    constructor(wallet: WalletEntity, boc: String) : this(
        wallet = wallet,
        boc = boc.cellFromBase64()
    )
}
