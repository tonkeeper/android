package com.tonapps.blockchain.ton.extensions

import com.tonapps.log.L
import org.ton.bitstring.BitString
import org.ton.block.Either
import org.ton.block.ExtInMsgInfo
import org.ton.block.Message
import org.ton.cell.Cell
import org.ton.cell.buildCell

val Message<Cell>.bodyCell: Cell
    get() = when (val b = body) {
        is Either.Left -> b.value
        is Either.Right -> b.value.value
    }

fun Message<Cell>.normalizeHash(): BitString {
    val body = bodyCell
    val msgInfo = info as? ExtInMsgInfo ?: return bodyCell.hash()
    val cell = buildCell {
        storeUInt(2, 2)
        storeUInt(0, 2)
        storeAddress(msgInfo.dest)
        storeUInt(0, 4)
        storeBit(false)
        storeBit(true)
        storeRef(body)
    }
    return cell.hash()
}
