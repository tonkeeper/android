package com.tonapps.blockchain.ton.extensions

import org.ton.cell.CellBuilder

fun CellBuilder.storeBuilder(builder: CellBuilder): CellBuilder = apply {
    storeRefs(builder.refs)
    storeBits(builder.bits)
}

fun CellBuilder.storeSeqAndValidUntil(seqno: Int, validUntil: Long) = apply {
    if (seqno == 0) {
        for (i in 0 until 32) {
            storeBit(true)
        }
    } else {
        storeUInt(validUntil, 32)
    }
    storeUInt(seqno, 32)
}