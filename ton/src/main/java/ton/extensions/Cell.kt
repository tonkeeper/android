package ton.extensions

import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.crypto.base64

fun cellOf(value: String): Cell {
    return BagOfCells(base64(value)).first()
}

fun Cell.base64(): String {
    return base64(BagOfCells(this).toByteArray())
}

fun CellSlice.loadRemainingBits(): BitString {
    return BitString((this.bitsPosition until this.bits.size).map { this.loadBit() })
}

fun CellSlice.loadRemainingBitsAll(): BitString {
    var r = BitString((this.bitsPosition until this.bits.size).map { this.loadBit() })
    if (this.refs.isNotEmpty()) {
        r += this.refs.first().beginParse().loadRemainingBitsAll()
    }

    return r
}
