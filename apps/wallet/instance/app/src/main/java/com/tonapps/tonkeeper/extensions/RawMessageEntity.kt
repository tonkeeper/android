package com.tonapps.tonkeeper.extensions

import com.tonapps.blockchain.ton.ExcessesAddressRewriter
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.extensions.loadAddress
import com.tonapps.blockchain.ton.extensions.loadCoins
import com.tonapps.blockchain.ton.extensions.loadMaybeRef
import com.tonapps.blockchain.ton.extensions.loadOpCode
import com.tonapps.blockchain.ton.toBigInteger
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.tlb.CellRef

private fun rebuildJettonTransferWithCustomPayload(
    payload: Cell,
    newCustomPayload: Cell,
): Cell {
    val slice = payload.beginParse()
    val opCode = slice.loadOpCode()
    if (opCode != TONOpCode.JETTON_TRANSFER) {
        return newCustomPayload
    }

    val queryId = slice.loadUInt(64)
    val jettonAmount = slice.loadCoins()
    val receiverAddress = slice.loadAddress()
    val excessesAddress = slice.loadAddress()
    val customPayload = slice.loadMaybeRef()
    if (customPayload != null) {
        return payload
    }

    val forwardAmount = slice.loadCoins()
    val forwardBody = slice.loadMaybeRef()

    return TonTransferHelper.jetton(
        coins = jettonAmount,
        toAddress = receiverAddress,
        responseAddress = excessesAddress,
        queryId = queryId.toBigInteger(),
        forwardAmount = forwardAmount,
        forwardPayload = forwardBody,
        customPayload = newCustomPayload
    )
}

fun RawMessageEntity.getWalletTransfer(
    excessesAddress: AddrStd? = null,
    newStateInit: CellRef<StateInit>? = null,
    newCustomPayload: Cell? = null,
    sendMode: Int,
): WalletTransfer {
    val payload = getPayload()
    val body = if (excessesAddress != null) {
        ExcessesAddressRewriter.rewrite(payload, excessesAddress)
    } else if (newCustomPayload != null) {
        rebuildJettonTransferWithCustomPayload(payload, newCustomPayload)
    } else {
        payload
    }

    getStateInitRef()?.let {
        DevSettings.tonConnectLog("parsedStateInit: $it")
    }

    if (!payload.isEmpty()) {
        DevSettings.tonConnectLog("parsedPayload: $body")
    }

    val builder = WalletTransferBuilder()
    builder.destination = address
    builder.messageData = MessageData.Raw(body, newStateInit ?: getStateInitRef(), null) // TODO TONSDK
    builder.bounceable = addressTags.isBounceable
    if (newCustomPayload != null) {
        val defCoins = Coins.of(5, 17)
        if (defCoins.amount.value > coins.amount.value) {
            builder.coins = defCoins
        } else {
            builder.coins = coins
        }
    } else {
        builder.coins = coins
    }
    builder.sendMode = sendMode
    return builder.build()
}

fun RawMessageEntity.getDefaultWalletTransfer(sendMode: Int): WalletTransfer {
    val builder = WalletTransferBuilder()
    builder.destination = address
    builder.messageData = MessageData.Raw(getPayload(), getStateInitRef(), null) // TODO TONSDK
    builder.bounceable = addressTags.isBounceable
    builder.coins = coins
    builder.sendMode = sendMode
    return builder.build()
}