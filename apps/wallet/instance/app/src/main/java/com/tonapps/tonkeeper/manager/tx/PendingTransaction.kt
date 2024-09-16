package com.tonapps.tonkeeper.manager.tx

import android.util.Log
import com.tonapps.blockchain.ton.extensions.parseCell
import com.tonapps.security.hex
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.ton.cell.Cell

data class PendingTransaction(
    val wallet: WalletEntity,
    val boc: Cell,
    val timestamp: Long = System.currentTimeMillis()
) {

    val hash: String = hex(boc.hash())

    constructor(wallet: WalletEntity, boc: String) : this(
        wallet = wallet,
        boc = boc.parseCell()
    )
}