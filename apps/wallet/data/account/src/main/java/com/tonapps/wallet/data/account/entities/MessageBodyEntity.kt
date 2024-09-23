package com.tonapps.wallet.data.account.entities

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer

data class MessageBodyEntity(
    val wallet: WalletEntity,
    val seqNo: Int,
    val validUntil: Long,
    val transfers: List<WalletTransfer>,
) {

    fun createUnsignedBody(internalMessage: Boolean): Cell {
        return wallet.createBody(
            seqNo = seqNo,
            validUntil = validUntil,
            gifts = transfers,
            internalMessage = internalMessage
        )
    }

    fun createSignedBody(
        privateKey: PrivateKeyEd25519,
        internalMessage: Boolean
    ): Cell {
        val unsignedBody = createUnsignedBody(internalMessage)
        return wallet.sign(
            privateKey = privateKey,
            seqNo = seqNo,
            body = unsignedBody
        )
    }
}