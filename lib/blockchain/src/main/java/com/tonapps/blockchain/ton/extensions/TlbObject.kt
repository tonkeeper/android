package com.tonapps.blockchain.ton.extensions

import android.os.Parcelable
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.crypto.base64
import org.ton.tlb.TlbCodec
import org.ton.tlb.TlbObject
import org.ton.tlb.loadTlb
import org.ton.tlb.storeTlb

inline fun <reified T : TlbObject> T.toCell(): Cell {
    val codec = T::class.java.getMethod("tlbCodec").invoke(null) as TlbCodec<T>
    return CellBuilder()
        .storeTlb(codec, this)
        .endCell()
}


inline fun <reified T : TlbObject> T.bocBase64(): String {
    val cell = this.toCell()
    val array = BagOfCells(cell).toByteArray()
    return base64(array)
}

inline fun <reified T: TlbObject> String.toTlb(): T? {
    val boc = safeParseCell() ?: return null
    return boc.parse { loadTlb(T::class.java.getMethod("tlbCodec").invoke(null) as TlbCodec<T>) }
}