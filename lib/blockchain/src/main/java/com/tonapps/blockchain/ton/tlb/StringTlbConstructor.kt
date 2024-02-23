package com.tonapps.blockchain.ton.tlb

import com.tonapps.blockchain.ton.extensions.loadRemainingBits
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.cell.invoke
import org.ton.tlb.TlbConstructor

object StringTlbConstructor : TlbConstructor<String>(schema = "", id = null) {

    override fun storeTlb(
        cellBuilder: CellBuilder,
        value: String
    ) = cellBuilder {
        storeUInt(0, 32)
        storeBytes(value.toByteArray())
    }

    override fun loadTlb(
        cellSlice: CellSlice
    ): String = cellSlice {
        if (bits.size >= 32) {
            try {
                loadUInt32()
                return String(loadRemainingBits().toByteArray())
            } catch (ignored: Throwable) {}
        }
        return ""
    }
}