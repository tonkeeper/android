package com.tonapps.tonkeeper.extensions

import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.extensions.loadMaybeRef
import com.tonapps.blockchain.ton.extensions.loadOpCode
import com.tonapps.blockchain.ton.extensions.storeAddress
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.tlb.loadTlb
import org.ton.tlb.storeTlb

private fun RawMessageEntity.rebuildBodyWithCustomExcessesAccount(
    excessesAddress: AddrStd
): Cell {
    val slice = payload.beginParse()
    val builder = CellBuilder.beginCell()
    return when (slice.loadOpCode()) {
        // stonfi swap
        TONOpCode.STONFI_SWAP -> {
            builder
                .storeOpCode(TONOpCode.STONFI_SWAP)
                .storeAddress(slice.loadTlb(MsgAddressInt.tlbCodec()))
                .storeCoins(slice.loadTlb(Coins.tlbCodec()))
                .storeAddress(slice.loadTlb(MsgAddressInt.tlbCodec()))

            if (slice.loadBit()) {
                slice.loadTlb(MsgAddressInt.tlbCodec())
            }
            slice.endParse()

            builder
                .storeBit(true)
                .storeTlb(MsgAddressInt, excessesAddress)

            builder.endCell()
        }
        TONOpCode.NFT_TRANSFER -> payload
        TONOpCode.JETTON_TRANSFER -> payload
        else -> payload
    }
}

private fun RawMessageEntity.rebuildJettonTransferWithCustomPayload(
    newCustomPayload: Cell,
): Cell {
    val slice = payload.beginParse()
    val opCode = slice.loadOpCode()
    if (opCode != TONOpCode.JETTON_TRANSFER) {
        return newCustomPayload
    }

    val queryId = slice.loadUInt(64)
    val jettonAmount = slice.loadTlb(Coins.tlbCodec())
    val receiverAddress = slice.loadTlb(MsgAddressInt.tlbCodec())
    val excessesAddress = slice.loadTlb(MsgAddressInt.tlbCodec())
    val customPayload = slice.loadMaybeRef()
    if (customPayload != null) {
        return payload
    }

    val forwardAmount = slice.loadTlb(Coins.tlbCodec()).amount.toLong()
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
    newStateInit: StateInit? = null,
    newCustomPayload: Cell? = null,
): WalletTransfer {
    val builder = WalletTransferBuilder()
    builder.stateInit = stateInit ?: newStateInit
    builder.destination = address
    builder.body = if (excessesAddress != null) {
        rebuildBodyWithCustomExcessesAccount(excessesAddress)
    } else if (newCustomPayload != null) {
        rebuildJettonTransferWithCustomPayload(newCustomPayload)
    } else {
        payload
    }
    // builder.bounceable = address.isBounceable()
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