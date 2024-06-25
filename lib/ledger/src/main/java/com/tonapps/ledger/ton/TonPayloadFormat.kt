package com.tonapps.ledger.ton

import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell

sealed class TonPayloadFormat {

    data class Comment(val text: String) : TonPayloadFormat()

    data class JettonTransfer(
        val queryId: Long?,
        val amount: Coins,
        val destination: AddrStd,
        val responseDestination: AddrStd,
        val customPayload: Cell?,
        val forwardAmount: Coins,
        val forwardPayload: Cell?
    ) : TonPayloadFormat()

    data class NftTransfer(
        val queryId: Long?,
        val newOwner: AddrStd,
        val responseDestination: AddrStd,
        val customPayload: Cell?,
        val forwardAmount: Coins,
        val forwardPayload: Cell?
    ) : TonPayloadFormat()
}
