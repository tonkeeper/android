package com.tonapps.blockchain.ton

import com.tonapps.blockchain.ton.extensions.loadAddress
import com.tonapps.blockchain.ton.extensions.loadCoins
import com.tonapps.blockchain.ton.extensions.loadMaybeAddress
import com.tonapps.blockchain.ton.extensions.loadMaybeRef
import com.tonapps.blockchain.ton.extensions.loadOpCode
import com.tonapps.blockchain.ton.extensions.storeAddress
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeMaybeRef
import com.tonapps.blockchain.ton.extensions.storeOpCode
import org.ton.block.MsgAddress
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.cell.buildCell
import org.ton.tlb.loadTlb
import org.ton.tlb.storeTlb

private const val OP_CODE_BITS = 32
private const val QUERY_ID_BITS = 64
private const val TX_DEADLINE_BITS = 64

object ExcessesAddressRewriter {

    fun rewrite(payload: Cell, excessesAddress: MsgAddressInt): Cell {
        return try {
            rewriteBody(payload, excessesAddress)
        } catch (e: Exception) {
            payload
        }
    }

    private fun rewriteBody(payload: Cell, excessesAddress: MsgAddressInt): Cell {
        val slice = payload.beginParse()
        if (slice.remainingBits < OP_CODE_BITS) {
            return payload
        }
        return when (slice.loadOpCode()) {
            TONOpCode.JETTON_TRANSFER -> buildCell { storeJettonTransfer(slice, excessesAddress) }
            TONOpCode.STONFI_SWAP -> buildCell { storeStonfiSwap(slice, excessesAddress) }
            TONOpCode.STONFI_SWAP_V2 -> buildCell { storeStonfiSwapV2(slice, excessesAddress) }
            TONOpCode.PTON_TON_TRANSFER -> buildCell { storePtonTransfer(slice, excessesAddress) }
            else -> payload
        }
    }

    private fun CellBuilder.storeJettonTransfer(slice: CellSlice, excessesAddress: MsgAddressInt) {
        storeOpCode(TONOpCode.JETTON_TRANSFER)
        storeUInt(slice.loadUInt(QUERY_ID_BITS), QUERY_ID_BITS)
        storeCoins(slice.loadCoins())
        storeAddress(slice.loadAddress())
        slice.loadMaybeAddress()
        storeAddress(excessesAddress)
        storeMaybeRef(slice.loadMaybeRef())
        storeCoins(slice.loadCoins())
        storeEitherPayload(slice, excessesAddress)
    }

    private fun CellBuilder.storeStonfiSwap(slice: CellSlice, excessesAddress: MsgAddressInt) {
        storeOpCode(TONOpCode.STONFI_SWAP)
        storeAddress(slice.loadAddress())
        storeCoins(slice.loadCoins())
        storeAddress(slice.loadAddress())
        if (slice.loadBit()) {
            slice.loadMaybeAddress()
        }
        storeBit(true)
        storeAddress(excessesAddress)
        storeRemaining(slice)
    }

    private fun CellBuilder.storeStonfiSwapV2(slice: CellSlice, excessesAddress: MsgAddressInt) {
        storeOpCode(TONOpCode.STONFI_SWAP_V2)
        storeAddress(slice.loadAddress())
        storeAddress(slice.loadAddress())
        slice.loadMaybeAddress()
        storeAddress(excessesAddress)
        storeUInt(slice.loadUInt(TX_DEADLINE_BITS), TX_DEADLINE_BITS)
        storeRemaining(slice)
    }

    private fun CellBuilder.storePtonTransfer(slice: CellSlice, excessesAddress: MsgAddressInt) {
        storeOpCode(TONOpCode.PTON_TON_TRANSFER)
        storeUInt(slice.loadUInt(QUERY_ID_BITS), QUERY_ID_BITS)
        storeCoins(slice.loadCoins())
        storeTlb(MsgAddress, slice.loadTlb(MsgAddress))
        storeEitherPayload(slice, excessesAddress)
    }

    private fun CellBuilder.storeEitherPayload(slice: CellSlice, excessesAddress: MsgAddressInt) {
        val asRef = slice.loadBit()
        storeBit(asRef)
        if (asRef) {
            storeRef(rewriteBody(slice.loadRef(), excessesAddress))
            storeRemaining(slice)
        } else {
            val inline = rewriteBody(slice.loadRemainingCell(), excessesAddress)
            storeBits(inline.bits)
            storeRefs(inline.refs)
        }
    }

    private fun CellBuilder.storeRemaining(slice: CellSlice) {
        storeBits(slice.loadBits(slice.remainingBits))
        while (slice.remainingRefs > 0) {
            storeRef(slice.loadRef())
        }
    }

    private fun CellSlice.loadRemainingCell(): Cell = buildCell {
        storeRemaining(this@loadRemainingCell)
    }
}
