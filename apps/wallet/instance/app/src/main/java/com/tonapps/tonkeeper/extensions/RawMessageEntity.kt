package com.tonapps.tonkeeper.extensions

import android.util.Log
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.extensions.isBounceable
import com.tonapps.blockchain.ton.extensions.loadAddress
import com.tonapps.blockchain.ton.extensions.loadCoins
import com.tonapps.blockchain.ton.extensions.loadMaybeAddress
import com.tonapps.blockchain.ton.extensions.loadMaybeRef
import com.tonapps.blockchain.ton.extensions.loadOpCode
import com.tonapps.blockchain.ton.extensions.storeAddress
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.ledger.ton.remainingRefs
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.tlb.CellRef

private fun rebuildJettonWithCustomExcessesAccount(
    payload: Cell,
    slice: CellSlice,
    builder: CellBuilder,
    excessesAddress: AddrStd
): Cell {

    try {
        builder
            .storeOpCode(TONOpCode.JETTON_TRANSFER)
            .storeUInt(slice.loadUInt(64), 64)
            .storeCoins(slice.loadCoins())
            .storeAddress(slice.loadAddress())

        slice.loadMaybeAddress()

        while (slice.remainingRefs > 0) {
            val forwardCell = slice.loadRef()
            builder.storeRef(rebuildBodyWithCustomExcessesAccount(forwardCell, excessesAddress))
        }
        return builder
            .storeAddress(excessesAddress)
            .storeBits(slice.loadBits(slice.remainingBits))
            .endCell()
    } catch (e: Throwable) {
        return payload
    }
}

private fun rebuildBodyWithCustomExcessesAccount(
    payload: Cell,
    excessesAddress: AddrStd
): Cell {
    val slice = payload.beginParse()
    val builder = CellBuilder.beginCell()
    val opCode = slice.loadOpCode()
    return when (opCode) {
        // stonfi swap
        TONOpCode.STONFI_SWAP -> {
            builder
                .storeOpCode(TONOpCode.STONFI_SWAP)
                .storeAddress(slice.loadAddress())
                .storeCoins(slice.loadCoins())
                .storeAddress(slice.loadAddress())

            if (slice.loadBit()) {
                slice.loadAddress()
            }
            slice.endParse()
            builder
                .storeBit(true)
                .storeAddress(excessesAddress)
                .endCell()
        }
        // stonfi swap v2
        TONOpCode.STONFI_SWAP_V2 -> {
            builder
                .storeOpCode(TONOpCode.STONFI_SWAP_V2)
                .storeAddress(slice.loadAddress()) // token_wallet1
                .storeAddress(slice.loadAddress()) // refund_address
            slice.loadAddress()
            builder
                .storeAddress(excessesAddress) // excesses_address
                .storeUInt64(slice.loadUInt64()) // tx_deadline
                .storeRefs(slice.loadRef())
            slice.endParse()
            builder.endCell()
        }

        TONOpCode.NFT_TRANSFER -> payload
        TONOpCode.JETTON_TRANSFER -> rebuildJettonWithCustomExcessesAccount(
            payload,
            slice,
            builder,
            excessesAddress
        )

        else -> payload
    }
}

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

    val forwardAmount = slice.loadCoins().amount.toLong()
    val forwardBody = slice.loadMaybeRef()

    return TonTransferHelper.jetton(
        coins = jettonAmount,
        toAddress = receiverAddress,
        responseAddress = excessesAddress,
        queryId = queryId,
        forwardAmount = forwardAmount,
        forwardPayload = forwardBody,
        customPayload = newCustomPayload
    )
}

fun RawMessageEntity.getWalletTransfer(
    excessesAddress: AddrStd? = null,
    newStateInit: CellRef<StateInit>? = null,
    newCustomPayload: Cell? = null,
): WalletTransfer {
    val payload = getPayload()
    val body = if (excessesAddress != null) {
        rebuildBodyWithCustomExcessesAccount(payload, excessesAddress)
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
    builder.messageData = MessageData.Raw(body, newStateInit ?: getStateInitRef())
    builder.bounceable = addressValue.isBounceable()
    if (newCustomPayload != null) {
        val defCoins = Coins.of(0.5)
        if (defCoins.amount.value > coins.amount.value) {
            builder.coins = defCoins
        } else {
            builder.coins = coins
        }
    } else {
        builder.coins = coins
    }
    return builder.build()
}

fun RawMessageEntity.getDefaultWalletTransfer(): WalletTransfer {
    val builder = WalletTransferBuilder()
    builder.destination = address
    builder.messageData = MessageData.Raw(getPayload(), getStateInitRef())
    builder.bounceable = addressValue.isBounceable()
    builder.coins = coins
    return builder.build()
}