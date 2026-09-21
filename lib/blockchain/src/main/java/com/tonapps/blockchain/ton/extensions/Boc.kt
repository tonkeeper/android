package com.tonapps.blockchain.ton.extensions

import org.ton.bitstring.BitString
import org.ton.cell.Cell

private const val BOC_GENERIC_MAGIC = 0xB5EE9C72.toInt()

private data class CellKey(
    private val d1: Byte,
    private val d2: Byte,
    private val hash: BitString,
)

private val Cell.key: CellKey
    get() = CellKey(descriptor.d1, descriptor.d2, hash())

fun Cell.toBoc(): ByteArray {
    val cells = topologicalOrder()
    val indexes = HashMap<CellKey, Int>(cells.size)
    cells.forEachIndexed { index, cell -> indexes[cell.key] = index }

    var sizeBytes = 0
    while (cells.size >= (1L shl (sizeBytes shl 3))) {
        sizeBytes++
    }

    val serializedCells = cells.map { cell ->
        val data = cell.bits.toByteArray(augment = true)
        val bytes = ByteArray(2 + data.size + cell.refs.size * sizeBytes)
        bytes[0] = cell.descriptor.d1
        bytes[1] = cell.descriptor.d2
        data.copyInto(bytes, 2)
        var offset = 2 + data.size
        for (reference in cell.refs) {
            val refIndex = indexes[reference.key]
                ?: throw IllegalStateException("Cell reference is missing in the bag of cells")
            offset = bytes.writeInt(offset, refIndex, sizeBytes)
        }
        bytes
    }

    val totalSize = serializedCells.sumOf { it.size }
    var offsetBytes = 0
    while (totalSize >= (1L shl (offsetBytes shl 3))) {
        offsetBytes++
    }

    val output = ByteArray(6 + sizeBytes * 4 + offsetBytes + totalSize)
    var offset = output.writeInt(0, BOC_GENERIC_MAGIC, 4)
    offset = output.writeInt(offset, sizeBytes, 1)
    offset = output.writeInt(offset, offsetBytes, 1)
    offset = output.writeInt(offset, cells.size, sizeBytes)
    offset = output.writeInt(offset, 1, sizeBytes)
    offset = output.writeInt(offset, 0, sizeBytes)
    offset = output.writeInt(offset, totalSize, offsetBytes)
    offset = output.writeInt(offset, 0, sizeBytes)
    for (bytes in serializedCells) {
        bytes.copyInto(output, offset)
        offset += bytes.size
    }
    return output
}

private fun Cell.topologicalOrder(): List<Cell> {
    val discovered = LinkedHashMap<CellKey, Cell>()
    val heights = HashMap<CellKey, Int>()
    height(discovered, heights, 0)
    return discovered.values.sortedByDescending { heights.getValue(it.key) }
}

private fun Cell.height(
    discovered: LinkedHashMap<CellKey, Cell>,
    heights: HashMap<CellKey, Int>,
    depth: Int,
): Int {
    if (depth > Cell.MAX_DEPTH) {
        throw IllegalStateException("Cell depth is too large")
    }
    val cellKey = key
    heights[cellKey]?.let { return it }
    discovered[cellKey] = this
    var height = 0
    for (reference in refs) {
        height = maxOf(height, reference.height(discovered, heights, depth + 1) + 1)
    }
    heights[cellKey] = height
    return height
}

private fun ByteArray.writeInt(offset: Int, value: Int, bytes: Int): Int {
    for (i in 0 until bytes) {
        this[offset + i] = (value ushr ((bytes - i - 1) shl 3)).toByte()
    }
    return offset + bytes
}
