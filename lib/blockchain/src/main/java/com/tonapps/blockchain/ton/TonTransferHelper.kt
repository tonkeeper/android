package com.tonapps.blockchain.ton

import com.tonapps.blockchain.ton.extensions.storeMaybeRef
import com.tonapps.blockchain.ton.extensions.storeOpCode
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.loadTlb
import org.ton.tlb.storeRef
import org.ton.tlb.storeTlb
import java.math.BigInteger

@Deprecated("Use classes from com.tonapps.blockchain.ton.tlb package instead.")
object TonTransferHelper {

    fun text(text: String?): Cell? {
        if (text.isNullOrEmpty()) {
            return null
        }

        return buildCell {
            storeUInt(0, 32)
            storeBytes(text.toByteArray())
        }
    }

    fun body(body: Any?): Cell? {
        if (body == null) {
            return null
        }
        return when (body) {
            is String -> text(body)
            is Cell -> body
            else -> null
        }
    }

    fun jetton(
        coins: Coins,
        toAddress: MsgAddressInt,
        responseAddress: MsgAddressInt,
        queryId: BigInteger = BigInteger.ZERO,
        forwardAmount: Long = 1L,
        forwardPayload: Any? = null,
        customPayload: Cell? = null
    ): Cell {
        val payload = body(forwardPayload)

        return buildCell {
            storeOpCode(TONOpCode.JETTON_TRANSFER)
            storeUInt(queryId, 64)
            storeTlb(Coins, coins)
            storeTlb(MsgAddressInt, toAddress)
            storeTlb(MsgAddressInt, responseAddress)
            storeMaybeRef(customPayload)
            storeTlb(Coins, Coins.ofNano(forwardAmount))
            storeMaybeRef(payload)
        }
    }

    fun nft(
        newOwnerAddress: MsgAddressInt,
        excessesAddress: MsgAddressInt,
        queryId: BigInteger = BigInteger.ZERO,
        forwardAmount: Long = 1L,
        body: Any? = null,
    ): Cell {
        val payload = body(body)

        return buildCell {
            storeOpCode(TONOpCode.NFT_TRANSFER)
            storeUInt(queryId, 64)
            storeTlb(MsgAddressInt, newOwnerAddress)
            storeTlb(MsgAddressInt, excessesAddress)
            storeBit(false)
            storeTlb(Coins, Coins.ofNano(forwardAmount))
            storeBit(payload != null)
            payload?.let {
                storeRef(AnyTlbConstructor, CellRef(it))
            }
        }
    }

}