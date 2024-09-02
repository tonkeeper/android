package com.tonapps.wallet.data.core.entity

import android.os.Parcelable
import android.util.Log
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.extensions.loadOpCode
import com.tonapps.blockchain.ton.extensions.safeParseCell
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.toTlb
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
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

@Parcelize
data class RawMessageEntity(
    val addressValue: String,
    val amount: Long,
    val stateInitValue: String?,
    val payloadValue: String
): Parcelable {

    val address: AddrStd
        get() = AddrStd.parse(addressValue)

    val coins: Coins
        get() = Coins.ofNano(amount)

    val stateInit: StateInit?
        get() = stateInitValue?.toTlb()

    val payload: Cell
        get() = payloadValue.safeParseCell() ?: Cell()

    fun getWalletTransfer(excessesAddress: AddrStd? = null): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.stateInit = stateInit
        builder.destination = address
        builder.body = if (excessesAddress != null) rebuildBodyWithCustomExcessesAccount(payload, excessesAddress) else payload
        // builder.bounceable = address.isBounceable()
        builder.coins = coins
        return builder.build()
    }

    private fun rebuildBodyWithCustomExcessesAccount(body: Cell, excessesAddress: AddrStd): Cell {
        val slice = body.beginParse()
        val builder = CellBuilder.beginCell()
        return when (slice.loadOpCode()) {
            // stonfi swap
            TONOpCode.STONFI_SWAP -> {
                builder
                    .storeOpCode(TONOpCode.STONFI_SWAP)
                    .storeTlb(MsgAddressInt, slice.loadTlb(AddrStd.tlbCodec()))
                    .storeTlb(Coins, slice.loadTlb(Coins.tlbCodec()))
                    .storeTlb(MsgAddressInt, slice.loadTlb(AddrStd.tlbCodec()))

                if (slice.loadBit()) {
                    slice.loadTlb(AddrStd.tlbCodec())
                }
                slice.endParse()

                builder
                    .storeBit(true)
                    .storeTlb(MsgAddressInt, excessesAddress)

                builder.endCell()
            }
            // nft transfer
            TONOpCode.NFT_TRANSFER -> body
            // jetton transfer
            TONOpCode.JETTON_TRANSFER -> body
            else -> body
        }
    }

    constructor(json: JSONObject) : this(
        json.getString("address"),
        parseAmount(json.get("amount")),
        json.optString("stateInit"),
        json.optString("payload")
    )

    private companion object {

        private fun parseAmount(value: Any): Long {
            if (value is Long) {
                return value
            }
            return value.toString().toLong()
        }
    }

}