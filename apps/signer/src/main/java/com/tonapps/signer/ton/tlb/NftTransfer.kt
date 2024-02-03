package com.tonapps.signer.ton.tlb

import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.cell.invoke
import org.ton.tlb.CellRef
import org.ton.tlb.TlbCodec
import org.ton.tlb.TlbConstructor
import org.ton.tlb.TlbObject
import org.ton.tlb.TlbPrettyPrinter
import org.ton.tlb.loadTlb
import org.ton.tlb.providers.TlbConstructorProvider
import org.ton.tlb.storeRef
import org.ton.tlb.storeTlb

data class NftTransfer(
    val queryId: Long = 0L,
    val newOwnerAddress: MsgAddressInt,
    val excessesAddress: MsgAddressInt,
    val forwardAmount: Coins,
    val comment: String?
) : TlbObject {

    override fun print(printer: TlbPrettyPrinter): TlbPrettyPrinter = printer.type("NftTransfer") {
        field("queryId", queryId)
        field("newOwnerAddress", newOwnerAddress)
        field("excessesAddress", excessesAddress)
        field("forwardAmount", forwardAmount)
        field("comment", comment)
    }

    companion object : TlbConstructorProvider<NftTransfer> by NftTransferTlbConstructor {

        @JvmStatic
        fun tlbCodec(): TlbCodec<NftTransfer> = NftTransferTlbConstructor
    }
}

private object NftTransferTlbConstructor : TlbConstructor<NftTransfer>(
    schema = "", id = null
) {

    override fun storeTlb(
        cellBuilder: CellBuilder, value: NftTransfer
    ) = cellBuilder {
        storeUInt(0x5fcc3d14, 32)
        storeUInt(value.queryId, 64)
        storeTlb(MsgAddressInt, value.newOwnerAddress)
        storeTlb(MsgAddressInt, value.excessesAddress)
        storeBit(false)
        storeTlb(Coins, value.forwardAmount)
        storeBit(value.comment != null)
        value.comment?.let {
            storeRef(StringTlbConstructor, CellRef(it))
        }
    }

    override fun loadTlb(
        cellSlice: CellSlice
    ): NftTransfer = cellSlice {
        loadUInt32()
        val queryId = loadUInt64().toLong()
        val newOwnerAddress = loadTlb(MsgAddressInt)
        val excessesAddress = loadTlb(MsgAddressInt)
        loadBit()
        val forwardAmount = loadTlb(Coins)
        val comment = if (loadBit()) loadTlb(StringTlbConstructor) else null
        NftTransfer(queryId, newOwnerAddress, excessesAddress, forwardAmount, comment)
    }
}