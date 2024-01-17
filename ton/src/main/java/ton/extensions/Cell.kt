package ton.extensions

import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.crypto.base64

fun cellOf(value: String): Cell {
    return BagOfCells(base64(value)).first()
}