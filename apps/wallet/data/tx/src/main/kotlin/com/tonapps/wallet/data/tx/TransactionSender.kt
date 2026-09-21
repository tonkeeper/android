package com.tonapps.wallet.data.tx

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.extensions.base64
import org.ton.cell.Cell

class TransactionSender internal constructor(
    private val transactionManager: TransactionManager,
) {

    suspend fun send(
        wallet: WalletEntity,
        boc: String,
        withBattery: Boolean,
        source: String,
        confirmationTime: Double,
        headers: Map<String, String>,
    ) = transactionManager.send(
        wallet = wallet,
        boc = boc,
        withBattery = withBattery,
        source = source,
        confirmationTime = confirmationTime,
        headers = headers,
    )

    suspend fun send(
        wallet: WalletEntity,
        boc: Cell,
        withBattery: Boolean,
        source: String,
        confirmationTime: Double,
        headers: Map<String, String>,
    ) = send(
        wallet = wallet,
        boc = boc.base64(),
        withBattery = withBattery,
        source = source,
        confirmationTime = confirmationTime,
        headers = headers,
    )
}
